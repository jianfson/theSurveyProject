import cv2
import numpy as np
import scipy.io as sio
import matplotlib.pyplot as plt
from sklearn.neighbors import NearestNeighbors
from sklearn.neighbors import KDTree
from scipy import interpolate
import time

def get_subset(x1, x2, maxAttempts):
    line_thr = 4000
    epoch = 0
    data_size = x1.shape[0]
#    matchesMask = np.zeros(data_size)
    while epoch < maxAttempts:
        exit_flag = False
        idxs = np.arange(data_size)
        np.random.shuffle(idxs)
        maybe_idxs = idxs[:4]
        maybe_random = x1[maybe_idxs,:]
        maybe_random_target = x2[maybe_idxs,:]
#        print("get_subset epoch: %d"%epoch)
        for i in range(4):
            for j in range(i+1, 4):
                for k in range(j+1, 4):
                    x_point1, y_point1 = maybe_random[j][0] - maybe_random[i][0], maybe_random[j][1] - maybe_random[i][1]
                    x_point2, y_point2 = maybe_random[k][0] - maybe_random[i][0], maybe_random[k][1] - maybe_random[i][1]
                    x_point1_target, y_point1_target = maybe_random_target[j][0] - maybe_random_target[i][0], maybe_random_target[j][1] - maybe_random_target[i][1]
                    x_point2_target, y_point2_target = maybe_random_target[k][0] - maybe_random_target[i][0], maybe_random_target[k][1] - maybe_random_target[i][1]
#                    print("source line detect: %f"%abs(x_point1 * y_point2-x_point2 * y_point1))
#                    print("target line detect: %f"%abs(x_point1_target * y_point2_target-x_point2_target * y_point1_target))
                    if abs(x_point1 * y_point2-x_point2 * y_point1) < line_thr or abs(x_point1_target * y_point2_target-x_point2_target * y_point1_target) < line_thr:
#                        print("detected line: break")
                        exit_flag = True
                        break
                if exit_flag:
                    break
            if exit_flag:
                    break
        if exit_flag:
            epoch+=1
            continue
#        matchesMask[maybe_idxs[0]] = 1
#        matchesMask[maybe_idxs[1]] = 1
#        matchesMask[maybe_idxs[2]] = 1
#        matchesMask[maybe_idxs[3]] = 1
        return maybe_idxs

def find_inliers(x1, x2, A, ransac_thr, matchesMask):
    data_size = x1.shape[0]
    goodCount = 0
    for index in range(data_size):
        template_point = np.array(list(x1[index]) + [1])
        target_point = np.array(list(x2[index]) + [1])
        template_point_image = np.dot(A, template_point)
        error = np.sqrt(np.sum((template_point_image - target_point) ** 2))
#        print("find_inliers error: %f"%error)
        if error <= ransac_thr:
            goodCount += 1
            matchesMask[index] = 1
    return goodCount

def BilinearInterpolation(imgSrc:np.ndarray, h, w, sx:float, sy:float)->float:
    intSx, intSy = int(sx), int(sy)
    if 0 <= intSx  < w - 1 and 0 <= intSy < h - 1:
        x1, x2 = intSx, intSx + 1
        y1, y2 = intSy, intSy + 1
        H1 = np.dot(np.array([x2 - sx, sx - x1]), imgSrc[y1: y2 + 1, x1:x2 + 1])
        return H1[0]*(y2 - sy) + H1[1]*(sy - y1)
    else:
        return imgSrc[intSy, intSx]

def find_match(img1, img2):
    # Sift Feature Extraction
    sift = cv2.xfeatures2d.SIFT_create()
    kp1, des1 = sift.detectAndCompute(img1, None)
    kp2, des2 = sift.detectAndCompute(img2, None)
    ratio = 0.78

    # start match
    start = time.time()
    x1 = []
    x2 = []

    kdt = KDTree(np.matrix(des2))
    return_distance, indexes = kdt.query(np.matrix(des1), k=2)
    for m in range(return_distance.shape[0]):
        if return_distance[m][0]/return_distance[m][1] < ratio:
            x1.append(kp1[m].pt)
            x2.append(kp2[indexes[m][0]].pt)
            print("find_match: closed distance: %f"%return_distance[m][0])

    # backup: exhaustive method
#    for m in range(des1.shape[0]):
#        X = np.array([des1[m]])
#        X =np.vstack((X,des2))
#        nbrs = NearestNeighbors(n_neighbors=3, algorithm='ball_tree').fit(X)
#        distances,indices = nbrs.kneighbors(X)
#        if distances[0][1]/distances[0][2] < ratio:
#            x1.append(kp1[m].pt)
#            x2.append(kp2[indices[0][1]].pt)
#            print(distances[0][1])
    end = time.time()
    print("Match time : %.2f s"%(end-start))
#    np.save('x1.npy', x1)
#    np.save('x2.npy', x2)
    return np.array(x1), np.array(x2)

def align_image_using_feature(x1, x2, ransac_thr, ransac_iter):
    best_A = None
    maxGoodCount = 0
    best_matchesMask = []
    for i in range(ransac_iter):
        maybe_idxs = get_subset(x1, x2, 300)
        X1 = x1[maybe_idxs]
        X2 = x2[maybe_idxs]
        m = np.array([
            [X1[0][0], X1[0][1], 1, 0, 0, 0, -X2[0][0] * X1[0][0], -X2[0][0] * X1[0][1]],
            [0, 0, 0, X1[0][0], X1[0][1], 1, -X2[0][1] * X1[0][0], -X2[0][1] * X1[0][1]],
            [X1[1][0], X1[1][1], 1, 0, 0, 0, -X2[1][0] * X1[1][0], -X2[1][0] * X1[1][1]],
            [0, 0, 0, X1[1][0], X1[1][1], 1, -X2[1][1] * X1[1][0], -X2[1][1] * X1[1][1]],
            [X1[2][0], X1[2][1], 1, 0, 0, 0, -X2[2][0] * X1[2][0], -X2[2][0] * X1[2][1]],
            [0, 0, 0, X1[2][0], X1[2][1], 1, -X2[2][1] * X1[2][0], -X2[2][1] * X1[2][1]],
            [X1[3][0], X1[3][1], 1, 0, 0, 0, -X2[3][0] * X1[3][0], -X2[3][0] * X1[3][1]],
            [0, 0, 0, X1[3][0], X1[3][1], 1, -X2[3][1] * X1[3][0], -X2[3][1] * X1[3][1]],
        ])
        b = X2.reshape(-1)
        try:
            A = np.linalg.solve(m, b)
        except np.linalg.LinAlgError:
            continue
        A = list(A)
        A.append(1)
        A = np.array(A)
        A = A.reshape((3, 3))
        if best_A is None:
            best_A = A
        matchesMask = np.zeros(x1.shape[0])
        goodCount = find_inliers(x1, x2, A, ransac_thr, matchesMask)
#        epoch = np.log(1-0.995)/np.log(1-np.power(goodCount/x1.shape[0],4))
#        if epoch < ransac_iter:
#            break
#        print("ransac epoch: %f"%epoch)
        if goodCount > maxGoodCount:
            best_A = A
            maxGoodCount = goodCount
            best_matchesMask = matchesMask

    print('find_inliers count, #maxGoodCount/Total  = {}/{}'.format(maxGoodCount, x1.shape[0]))
    return best_A, best_matchesMask

def warp_image(img, A, output_size):
    img_warped = np.zeros(output_size)
    for r in range(output_size[0]):
        for s in range(output_size[1]):
            vts = np.dot(A, np.array([s, r, 1]))
            img_warped[r, s] = BilinearInterpolation(img, img.shape[0], img.shape[1], vts[0], vts[1])
    return img_warped

def align_image(template, target, A):
    print('Compute the gradient of template image:')
    x = np.array([[0, 0, 0], [-1, 0, 1], [0, 0, 0]])
    y = x.T
    padded = np.pad(template, 1)
    u, v = x.shape
    dx = np.zeros(template.shape)
    dy = np.zeros(template.shape)
    for i in range(template.shape[0]):
        for j in range(template.shape[1]):
            dx_pixel = 0
            dy_pixel = 0
            for u in range(x.shape[0]):
                for v in range(x.shape[1]):
                    dx_pixel += padded[i + u][j + v] * x[u][v]
                    dy_pixel += padded[i + u][j + v] * y[u][v]
            dx[i][j] = dx_pixel
            dy[i][j] = dy_pixel
    m, n = dx.shape
    gradient = np.zeros((m, n, 2))
    for i in range(m):
        for j in range(n):
            gradient[i][j] = [dx[i][j], dy[i][j]]
#    print(gradient)
    print(gradient.shape)

    print("Compute the Jacobian and the steepest decent images: ")
    steepest_img = np.zeros((template.shape[0], template.shape[1], 6))
    for i in range(template.shape[0]):
        for j in range(template.shape[1]):
            steepest_img[i, j] = np.dot(
                gradient[i, j],
                np.array([
                    [j, i, 1, 0, 0, 0],
                    [0, 0, 0, j, i, 1],
                ])
            )
    print(steepest_img.shape)

    print("Compute the 6 × 6 Hessian: ")
    hessian = np.zeros((6, 6))
    for i in range(template.shape[0]):
        for j in range(template.shape[1]):
            Hx = np.dot(steepest_img[i, j].reshape(6, 1), steepest_img[i, j].reshape(1, 6))
            hessian += Hx
    hessian_inv = np.linalg.inv(hessian)
    print(hessian)
    refined_A = A
    error_norms = []

    print('starting align_image...')
    epoch = 0
    iterations = 200
    error_thr = 1e3
    while True:
        img_warped = warp_image(target, refined_A, template.shape)
        error_img = img_warped - template
        error_norm = np.sqrt(np.sum(error_img ** 2))
        error_norms.append(error_norm)
        F = np.zeros((6, 1))
        for h in range(template.shape[0]):
            for w in range(template.shape[1]):
                F += (np.transpose(steepest_img[h, w]) * error_img[h, w]).reshape(6, 1)
        dell_p = np.dot(hessian_inv, F)
        p = np.array([[1 + dell_p[0][0], dell_p[1][0], dell_p[2][0]],
                    [dell_p[3][0], 1 + dell_p[4][0], dell_p[5][0]],
                    [0, 0, 1]
                    ])
        refined_A = np.dot(refined_A, np.linalg.inv(p))
        print('align_image epoch = {}, error_norm = {}'.format(epoch, error_norm))
        if error_norm < error_thr or epoch > iterations:
            break
        epoch += 1
    return refined_A, np.array(error_norms)

def track_multi_frames(template, img_list):
    x1, x2 = find_match(template, img_list[0])
    ransac_thr = 10
    ransac_iter = 1000
    A, matchesMask = align_image_using_feature(x1, x2, ransac_thr, ransac_iter)
    A_list = []
    for img in img_list:
        print('starting process image...')
        A, errors = align_image(template, img, A)
        template = warp_image(img, A, template.shape)
        A_list.append(A)
    return A_list


def visualize_find_match(img1, img2, x1, x2, img_h=500):
    assert x1.shape == x2.shape, 'x1 and x2 should have same shape!'
    scale_factor1 = img_h/img1.shape[0]
    scale_factor2 = img_h/img2.shape[0]
    img1_resized = cv2.resize(img1, None, fx=scale_factor1, fy=scale_factor1)
    img2_resized = cv2.resize(img2, None, fx=scale_factor2, fy=scale_factor2)
    x1 = x1 * scale_factor1
    x2 = x2 * scale_factor2
    x2[:, 0] += img1_resized.shape[1]
    img = np.hstack((img1_resized, img2_resized))
    plt.imshow(img, cmap='gray', vmin=0, vmax=255)
    for i in range(x1.shape[0]):
        plt.plot([x1[i, 0], x2[i, 0]], [x1[i, 1], x2[i, 1]], 'b')
        plt.plot([x1[i, 0], x2[i, 0]], [x1[i, 1], x2[i, 1]], 'bo')
    plt.axis('off')
    plt.show()

def visualize_align_image_using_feature(img1, img2, x1, x2, A, matchesMask, img_h=500):
    assert x1.shape == x2.shape, 'x1 and x2 should have same shape!'
    scale_factor1 = img_h/img1.shape[0]
    scale_factor2 = img_h/img2.shape[0]
    img1_resized = cv2.resize(img1, None, fx=scale_factor1, fy=scale_factor1)
    img2_resized = cv2.resize(img2, None, fx=scale_factor2, fy=scale_factor2)
    x1 = x1 * scale_factor1
    x2 = x2 * scale_factor2
    x2[:, 0] += img1_resized.shape[1]
    img = np.hstack((img1_resized, img2_resized))
    plt.imshow(img, cmap='gray', vmin=0, vmax=255)
    ul = np.dot(A, np.array([0, 0, 1]))
    ur = np.dot(A, np.array([img1.shape[1], 0, 1]))
    ll = np.dot(A, np.array([0, img1.shape[0], 1]))
    lr = np.dot(A, np.array([img1.shape[1], img1.shape[0], 1]))
    x3 = ul[0:2]
    x3 = np.vstack((x3, ll[0:2]))
    x3 = np.vstack((x3, lr[0:2]))
    x3 = np.vstack((x3, ur[0:2]))
    x3 = x3 * scale_factor2
    x3[:, 0] += img1_resized.shape[1]
    plt.plot([x3[0, 0], x3[1, 0], x3[2, 0], x3[3, 0], x3[0, 0]], [x3[0, 1], x3[1, 1], x3[2, 1], x3[3, 1], x3[0, 1]], 'r')
    for i in range(x1.shape[0]):
        if matchesMask[i] == 1:
            plt.plot([x1[i, 0], x2[i, 0]], [x1[i, 1], x2[i, 1]], 'y')
            plt.plot([x1[i, 0], x2[i, 0]], [x1[i, 1], x2[i, 1]], 'yo')
        else:
            plt.plot([x1[i, 0], x2[i, 0]], [x1[i, 1], x2[i, 1]], 'b')
            plt.plot([x1[i, 0], x2[i, 0]], [x1[i, 1], x2[i, 1]], 'bo')
    plt.axis('off')
    plt.show()

def visualize_align_image(template, target, A, A_refined, errors=None):
    img_warped_init = warp_image(target, A, template.shape)
    img_warped_optim = warp_image(target, A_refined, template.shape)
    err_img_init = np.abs(img_warped_init - template)
    err_img_optim = np.abs(img_warped_optim - template)
    img_warped_init = np.uint8(img_warped_init)
    img_warped_optim = np.uint8(img_warped_optim)
    overlay_init = cv2.addWeighted(template, 0.5, img_warped_init, 0.5, 0)
    overlay_optim = cv2.addWeighted(template, 0.5, img_warped_optim, 0.5, 0)
    plt.subplot(241)
    plt.imshow(template, cmap='gray')
    plt.title('Template')
    plt.axis('off')
    plt.subplot(242)
    plt.imshow(img_warped_init, cmap='gray')
    plt.title('Initial warp')
    plt.axis('off')
    plt.subplot(243)
    plt.imshow(overlay_init, cmap='gray')
    plt.title('Overlay')
    plt.axis('off')
    plt.subplot(244)
    plt.imshow(err_img_init, cmap='jet')
    plt.title('Error map')
    plt.axis('off')
    plt.subplot(245)
    plt.imshow(template, cmap='gray')
    plt.title('Template')
    plt.axis('off')
    plt.subplot(246)
    plt.imshow(img_warped_optim, cmap='gray')
    plt.title('Opt. warp')
    plt.axis('off')
    plt.subplot(247)
    plt.imshow(overlay_optim, cmap='gray')
    plt.title('Overlay')
    plt.axis('off')
    plt.subplot(248)
    plt.imshow(err_img_optim, cmap='jet')
    plt.title('Error map')
    plt.axis('off')
    plt.show()

    if errors is not None:
        plt.plot(errors * 255)
        plt.xlabel('Iteration')
        plt.ylabel('Error')
        plt.show()


def visualize_track_multi_frames(template, img_list, A_list):
    bbox_list = []
    for A in A_list:
        boundary_t = np.hstack((np.array([[0, 0], [template.shape[1], 0], [template.shape[1], template.shape[0]],
                                        [0, template.shape[0]], [0, 0]]), np.ones((5, 1)))) @ A[:2, :].T
        bbox_list.append(boundary_t)

    plt.subplot(221)
    plt.imshow(img_list[0], cmap='gray')
    plt.plot(bbox_list[0][:, 0], bbox_list[0][:, 1], 'r')
    plt.title('Frame 1')
    plt.axis('off')
    plt.subplot(222)
    plt.imshow(img_list[1], cmap='gray')
    plt.plot(bbox_list[1][:, 0], bbox_list[1][:, 1], 'r')
    plt.title('Frame 2')
    plt.axis('off')
    plt.subplot(223)
    plt.imshow(img_list[2], cmap='gray')
    plt.plot(bbox_list[2][:, 0], bbox_list[2][:, 1], 'r')
    plt.title('Frame 3')
    plt.axis('off')
    plt.subplot(224)
    plt.imshow(img_list[3], cmap='gray')
    plt.plot(bbox_list[3][:, 0], bbox_list[3][:, 1], 'r')
    plt.title('Frame 4')
    plt.axis('off')
    plt.show()


if __name__ == '__main__':
    template = cv2.imread('1_template.png', 0)  # read as grey scale image
    target_list = cv2.imread('2.jpg', 0)
    x1, x2 = find_match(template, target_list)
#    x1 = np.load('x1.npy')
#    x2 = np.load('x2.npy')
#    visualize_find_match(template, target_list, x1, x2)
#    exit()

    ransac_thr = 10
    ransac_iter = 1000
    A, matchesMask = align_image_using_feature(x1, x2, ransac_thr, ransac_iter)
#    np.save('A.npy', A)
    visualize_align_image_using_feature(template, target_list, x1, x2, A, matchesMask)
#    exit()
#    A = np.load('A.npy')
    img_warped = warp_image(target_list, A, template.shape)
    err_img_init = np.abs(img_warped - template)
    plt.imshow(img_warped, cmap='gray', vmin=0, vmax=255)
    plt.axis('off')
    plt.show()
    plt.imshow(err_img_init, cmap='jet')
    plt.show()
#    exit()

    A_refined, errors = align_image(template, target_list, A)
    visualize_align_image(template, target_list, A, A_refined, errors)
    exit()

    A_list = track_multi_frames(template, target_list)
    visualize_track_multi_frames(template, target_list, A_list)
