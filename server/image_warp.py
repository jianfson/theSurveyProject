#!/usr/bin/env python
# coding=utf-8

import numpy as np
import cv2
import math
import random
from scipy import misc, ndimage
import matplotlib.pyplot as plt
from PIL import Image
import matplotlib.cm as cm
import scipy.signal as signal
from skimage import feature, color, transform, io
import logging
import os.path

def compute_homography_and_warp(image, locations_clipx, locations_clipy, vp1, vp2, clip=True, clip_factor=3):
    vanishing_line = np.cross(vp1, vp2)
    H = np.eye(3)
    H[2] = vanishing_line / vanishing_line[2]
    H = H / H[2, 2]
    v_post1 = np.dot(H, vp1)
    v_post2 = np.dot(H, vp2)
    v_post1 = v_post1 / np.sqrt(v_post1[0]**2 + v_post1[1]**2)
    v_post2 = v_post2 / np.sqrt(v_post2[0]**2 + v_post2[1]**2)
    directions = np.array([[v_post1[0], -v_post1[0], v_post2[0], -v_post2[0]],
                           [v_post1[1], -v_post1[1], v_post2[1], -v_post2[1]]])
    thetas = np.arctan2(directions[0], directions[1])
    h_ind = np.argmin(np.abs(thetas))
    if h_ind // 2 == 0:
        v_ind = 2 + np.argmax([thetas[2], thetas[3]])
    else:
        v_ind = np.argmax([thetas[2], thetas[3]])
    A1 = np.array([[directions[0, v_ind], directions[0, h_ind], 0],
                   [directions[1, v_ind], directions[1, h_ind], 0],
                   [0, 0, 1]])
    if np.linalg.det(A1) < 0:
        A1[:, 0] = -A1[:, 0]
    A = np.linalg.inv(A1)
    inter_matrix = np.dot(A, H)
    cordsx = np.dot(inter_matrix, [locations_clipx[:,0].tolist(),locations_clipx[:,1].tolist(),np.ones(locations_clipx.shape[0]).tolist()])
    cordsx = cordsx[:2] / cordsx[2]
    tx = min(0, cordsx[0].min())
    max_x = cordsx[0].max() - tx
    cordsy = np.dot(inter_matrix, [[0, 0, image.shape[1], image.shape[1]],
                                  [min(locations_clipy[:,1]), max(locations_clipy[:,1]), min(locations_clipy[:,1]), max(locations_clipy[:,1])],
                                  [1, 1, 1, 1]])
#    cords = np.dot(inter_matrix, [[0, 0, image.shape[1], image.shape[1]],
#                                  [0, image.shape[0], 0, image.shape[0]],
#                                  [1, 1, 1, 1]])
    cordsy = cordsy[:2] / cordsy[2]
    ty = min(0, cordsy[1].min())
    max_y = cordsy[1].max() - ty
    if clip:
        max_offset = max(image.shape) * clip_factor / 2
        tx = max(tx, -max_offset)
        ty = max(ty, -max_offset)
        max_x = min(max_x, -tx + max_offset)
        max_y = min(max_y, -ty + max_offset)
    max_x = int(max_x)
    max_y = int(max_y)
    T = np.array([[1, 0, -tx],
                  [0, 1, -ty],
                  [0, 0, 1]])
    final_homography = np.dot(T, inter_matrix)
    warped_img = cv2.warpPerspective(image, final_homography, (max_x, max_y))
    warped_img = warped_img[ int(max(0, cordsy[1].min())) : max_y, int(max(0, cordsx[0].min())) : max_x ]
    return warped_img


def edgelet_lines(edgelets):
    locations, directions, _ = edgelets
    normals = np.zeros_like(directions)
    normals[:, 0] = directions[:, 1]
    normals[:, 1] = -directions[:, 0]
    p = -np.sum(locations * normals, axis=1)
    lines = np.concatenate((normals, p[:, np.newaxis]), axis=1)
    return lines


def compute_votes(edgelets, model, threshold_inlier=5):
    vp = model[:2] / model[2]
    locations, directions, strengths = edgelets
    est_directions = locations - vp
    dot_prod = np.sum(est_directions * directions, axis=1)
    abs_prod = np.linalg.norm(directions, axis=1) * \
        np.linalg.norm(est_directions, axis=1)
    abs_prod[abs_prod == 0] = 1e-5
    cosine_theta = dot_prod / abs_prod
    theta = np.arccos(np.abs(cosine_theta))
    theta_thresh = threshold_inlier * np.pi / 180
    return (theta < theta_thresh) * strengths


def ransac_vanishing_point(edgelets, num_ransac_iter=2000, threshold_inlier=5):
    locations, directions, strengths = edgelets
    lines = edgelet_lines(edgelets)
    num_pts = strengths.size
    arg_sort = np.argsort(-strengths)
    first_index_space = arg_sort[:num_pts // 5]
    second_index_space = arg_sort[:num_pts // 2]
    best_model = None
    best_votes = np.zeros(num_pts)
    for ransac_iter in range(num_ransac_iter):
        ind1 = np.random.choice(first_index_space)
        ind2 = np.random.choice(second_index_space)
        l1 = lines[ind1]
        l2 = lines[ind2]
        current_model = np.cross(l1, l2)
        if np.sum(current_model**2) < 1 or current_model[2] == 0:
            continue
        current_votes = compute_votes(
            edgelets, current_model, threshold_inlier)
        if current_votes.sum() > best_votes.sum():
            best_model = current_model
            best_votes = current_votes
            logging.info("Current best model has {} votes at iteration {}".format(
                current_votes.sum(), ransac_iter))
    return best_model


def vis_edgelets(image, edgelets, show=True):
    import matplotlib.pyplot as plt
    plt.figure(figsize=(10, 10))
    plt.imshow(image)
    locations, directions, strengths = edgelets
    for i in range(locations.shape[0]):
        xax = [locations[i, 0] - directions[i, 0] * strengths[i] / 2,
               locations[i, 0] + directions[i, 0] * strengths[i] / 2]
        yax = [locations[i, 1] - directions[i, 1] * strengths[i] / 2,
               locations[i, 1] + directions[i, 1] * strengths[i] / 2]
        plt.plot(xax, yax, 'r-')
    if show:
        plt.show()

def vis_model(image, edgelets, model, show=True):
    import matplotlib.pyplot as plt
    locations, directions, strengths = edgelets
    inliers = compute_votes(edgelets, model, 10) > 0
    edgelets = (locations[inliers], directions[inliers], strengths[inliers])
    locations, directions, strengths = edgelets
    vis_edgelets(image, edgelets, False)
    vp = model / model[2]
    plt.plot(vp[0], vp[1], 'bo')
    for i in range(locations.shape[0]):
        xax = [locations[i, 0], vp[0]]
        yax = [locations[i, 1], vp[1]]
        plt.plot(xax, yax, 'b-.')
    if show:
        plt.show()

def compute_edgelets(img, lines, threshold_inlier1, threshold_inlier2, threshold_strenth, threshold_clipx, threshold_clipy, min_grayx, max_grayx, min_grayy, max_grayy):
    locations1 = []
    directions1 = []
    strengths1 = []
    locations2 = []
    directions2 = []
    strengths2 = []
    theta_thresh1 = threshold_inlier1 * np.pi / 180
    theta_thresh2 = threshold_inlier2 * np.pi / 180
    locations_clipx = []
    locations_clipy = []
    for p in lines:
        p0, p1 = p[0:2], p[2:4]
        location = (p0 + p1) / 2
        direction = p1 - p0
        direction = np.array(direction) / np.linalg.norm(direction)
        est_directions1 = np.array([1,0])
        est_directions2 = np.array([0,1])
        dot_prod1 = np.sum(est_directions1 * direction)
        abs_prod1 = np.linalg.norm(direction) *np.linalg.norm(est_directions1)
        if abs_prod1 == 0:
            abs_prod1 == 1e-5
        cosine_theta1 = dot_prod1 / abs_prod1
        theta1 = np.arccos(np.abs(cosine_theta1))
        dot_prod2 = np.sum(est_directions2 * direction)
        abs_prod2 = np.linalg.norm(direction) *np.linalg.norm(est_directions2)
        if abs_prod2 == 0:
            abs_prod2 == 1e-5
        cosine_theta2 = dot_prod2 / abs_prod2
        theta2 = np.arccos(np.abs(cosine_theta2))
        strength = np.linalg.norm(p1 - p0)
        if theta1 < theta_thresh1 and strength > threshold_strenth:
            locations1.append(location)
            directions1.append(direction)
            strengths1.append(strength)
            if strength > threshold_clipy and img[int(location[1]), int(location[0])] > min_grayy and img[int(location[1]), int(location[0])] < max_grayy:
                locations_clipy.append(location)
        if theta2 < theta_thresh2 and strength > threshold_strenth:
            locations2.append(location)
            directions2.append(direction)
            strengths2.append(strength)
            if strength > threshold_clipx and img[int(location[1]), int(location[0])] > min_grayx and img[int(location[1]), int(location[0])] < max_grayx:
                locations_clipx.append(location)
    locations1 = np.array(locations1)
    directions1 = np.array(directions1)
    strengths1 = np.array(strengths1)
    locations2 = np.array(locations2)
    directions2 = np.array(directions2)
    strengths2 = np.array(strengths2)
    locations_clipx = np.array(locations_clipx)
    locations_clipy = np.array(locations_clipy)
    return (locations1, directions1, strengths1, locations2, directions2, strengths2, locations_clipx, locations_clipy)

def compute_edgelets_clip(edgelets, model, threshold_strenth):
    locations2, directions2, strengths2 = edgelets
    inliers = compute_votes(edgelets, model, 10) > 0
    edgelets = (locations2[inliers], directions2[inliers], strengths2[inliers])
    locations2, directions2, strengths2 = edgelets
    locations = []
    directions = []
    strengths = []
    for i in range(strengths2.shape[0]):
        if strengths2[i] > threshold_strenth:
            locations.append(locations2[i])
            directions.append(directions2[i])
            strengths.append(strengths2[i])
    locations = np.array(locations)
    directions = np.array(directions)
    strengths = np.array(strengths)
    return (locations, directions, strengths)

def convertjpg(jpgfile, width=1440, height=500):
    img=Image.open(jpgfile)
    try:
        new_img = img.resize((width, height), Image.BILINEAR)
        if new_img.mode == 'P':
            new_img = new_img.convert("RGB")
        if new_img.mode == 'RGBA':
            new_img = new_img.convert("RGB")
        return new_img
    except Exception as e:
        print(e)

def image_warp(image_path, save_path):
    #step1: warp image by using vanishing_points.
    #step2: detect feature picture.
    #step3: warp feature image by using Registration algrithm.
    #step4: compute the angles using Registration.
    #step5: Determine whether to rotate image or not by using angles.
    #step6: how to crop the rock feature?
    img = io.imread(image_path)
    img_copy = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    lsd = cv2.createLineSegmentDetector(0)
    lines = lsd.detect(img_copy)[0]
    lines = lines[:, 0]
    edgelets=compute_edgelets(img_copy, lines, 5, 30, 40, 40, 200, 100, 200, 100, 300)
    locations1, directions1, strengths1, locations2, directions2, strengths2, locations_clipx, locations_clipy = edgelets
    edgelets1 = (locations1, directions1, strengths1)
    edgelets2 = (locations2, directions2, strengths2)
    vp1 = ransac_vanishing_point(edgelets1, 2000, threshold_inlier=5)
    vp2 = ransac_vanishing_point(edgelets2, 2000, threshold_inlier=5)
    edgelets2 = compute_edgelets_clip(edgelets2, vp2, 60)
    locations2, directions2, strengths2 = edgelets2
    #inliers = compute_votes(edgelets2, vp2, 10) > 0
    #edgelets2 = (locations2[inliers], directions2[inliers], strengths2[inliers])
    #locations2, directions2, strengths2 = edgelets2
    warped_img = compute_homography_and_warp(img, locations2, locations_clipy, vp1, vp2, 4)
    io.imsave(save_path, warped_img)

def image_split(jpgfiles, outdir, width=1440, height=500):
    #step1: Determine whether to warp images based on the shape of all images.
    #step2: split images directly.
    joint = Image.new('RGB', (width, height*len(jpgfiles)))
    for i in range(len(jpgfiles)):
        convertfile = convertjpg(jpgfiles[i], width, height)
        joint.paste(convertfile, (0,height*i))
    joint.save(outdir)
