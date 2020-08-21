#!/usr/bin/env python
# coding=utf-8
import numpy as np
import cv2
import math
import random
from scipy import misc, ndimage
import matplotlib.pyplot as plt

img = cv2.imread('/media/jiangxin/data/theSurveyProject/theSurveyProject/rock_temp/1x.jpg')
img = cv2.GaussianBlur(img,(3,3),0)
gray = cv2.cvtColor(img,cv2.COLOR_BGR2GRAY)
edges = cv2.Canny(gray,50,250,apertureSize = 3)
cv2.imwrite("canny.jpg", edges)

# 进行霍夫直线运算
#lines = cv2.HoughLines(edges, 1, np.pi/180, 0)
lines = cv2.HoughLinesP(edges, 1, np.pi/180, 50, 100, minLineLength = 1437, maxLineGap = 2000)
print(len(lines))
# 对检测到的每一条线段
for line in lines:
    x1, y1, x2, y2 = line[0]
    cv2.line(edges, (x1, y1), (x2, y2), (255, 0, 255), 2)
cv2.imshow("line_detection", edges)
cv2.waitKey(0)
exit()
for line in lines:
    # 霍夫变换返回的是 r 和 theta 值
    rho, theta = line[0]
    a = np.cos(theta)
    b = np.sin(theta)
    # 确定x0 和 y0
    x0 = a * rho
    y0 = b * rho
    # 认为构建（x1,y1）,(x2, y2)
    x1 = int(x0 + 1000 * (-b))
    y1 = int(y0 + 1000 * a)
    x2 = int(x0 - 1000 * (-b))
    y2 = int(y0 - 1000 * a)
    # 用cv2.line( )函数在image上画直线
    #cv2.line(img, (x1, y1), (x2, y2), (0, 0, 255), 2)
cv2.imshow("line_detection", edges)
cv2.waitKey(0)
exit()

# 霍夫变换
lines = cv2.HoughLines(edges, 1, np.pi / 180, 0)
rotate_angle = 0
for rho, theta in lines[0]:
    a = np.cos(theta)
    b = np.sin(theta)
    x0 = a * rho
    y0 = b * rho
    x1 = int(x0 + 1000 * (-b))
    y1 = int(y0 + 1000 * (a))
    x2 = int(x0 - 1000 * (-b))
    y2 = int(y0 - 1000 * (a))
    if x1 == x2 or y1 == y2:
        continue
    t = float(y2 - y1) / (x2 - x1)
    rotate_angle = math.degrees(math.atan(t))
    if rotate_angle > 45:
        rotate_angle = -90 + rotate_angle
    elif rotate_angle < -45:
        rotate_angle = 90 + rotate_angle
print("rotate_angle : "+str(rotate_angle))
rotate_img = ndimage.rotate(img, rotate_angle)
#misc.imsave('ssss.png',rotate_img)
cv2.imshow("img", rotate_img)
cv2.waitKey(0)
