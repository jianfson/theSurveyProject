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

from vp_detection import VPDetection
length_thresh = 60
principal_point = None
focal_length = 1102.79
seed = 1337

img = '/media/jiangxin/data/theSurveyProject/theSurveyProject/server/rock_temp/3x.jpg'

vpd = VPDetection(length_thresh, principal_point, focal_length, seed)
vps = vpd.find_vps(img)
print(vps)
vpd.create_debug_VP_image(True)
exit()

img = cv2.imread('/media/jiangxin/data/theSurveyProject/theSurveyProject/server/rock_temp/1x.jpg')
img = cv2.GaussianBlur(img,(3,3),0)
gray = cv2.cvtColor(img,cv2.COLOR_BGR2GRAY)
edges = cv2.Canny(gray,50,250,apertureSize = 3)
#cv2.imwrite("canny.jpg", edges)

# 进行霍夫直线运算
#lines = cv2.HoughLines(edges, 1, np.pi/180, 0)
lines = cv2.HoughLinesP(edges, 1, np.pi/180, 100, 10, minLineLength = 337, maxLineGap = 15)
print(len(lines))
# 对检测到的每一条线段
for line in lines:
    x1, y1, x2, y2 = line[0]
    cv2.line(edges, (x1, y1), (x2, y2), (246, 20, 50), 2)
cv2.imshow("line_detection", edges)
cv2.waitKey(0)
exit()

img = cv2.imread(r'/media/jiangxin/data/theSurveyProject/theSurveyProject/rock_temp/1x.jpg', 1)
rows, cols, channels = img.shape
p1 = np.float32([[0,0], [rows-1,0], [0,cols-1], [rows-1,cols-1]])
p2 = np.float32([[184.1,202.198], [1316.74,191.2], [7.64,1050.747], [1413.38,1050.8]])
M = cv2.getPerspectiveTransform(p2,p1)
dst = cv2.warpPerspective(img, M, (rows, cols))
cv2.imwrite("dst.jpg", dst)
exit()

# 生成高斯算子的函数
def func(x,y,sigma=1):
    return 100*(1/(2*np.pi*sigma))*np.exp(-((x-2)**2+(y-2)**2)/(2.0*sigma**2))

# 生成标准差为5的5*5高斯算子
suanzi1 = np.fromfunction(func,(5,5),sigma=5)

# Laplace扩展算子
suanzi2 = np.array([[1, 1, 1],
                    [1,-8, 1],
                    [1, 1, 1]])

# 打开图像并转化成灰度图像
image = Image.open("/media/jiangxin/data/theSurveyProject/theSurveyProject/rock_temp/1x.jpg").convert("L")
image_array = np.array(image)

# 利用生成的高斯算子与原图像进行卷积对图像进行平滑处理
image_blur = signal.convolve2d(image_array, suanzi1, mode="same")

# 对平滑后的图像进行边缘检测
image2 = signal.convolve2d(image_blur, suanzi2, mode="same")

# 结果转化到0-255
image2 = (image2/float(image2.max()))*255

# 将大于灰度平均值的灰度值变成255（白色），便于观察边缘
image2[image2>image2.mean()] = 255
misc.imsave('test.jpg', image2)
img = cv2.imread('test.jpg')
img = cv2.GaussianBlur(img,(3,3),0)
gray = cv2.cvtColor(img,cv2.COLOR_BGR2GRAY)
edges = cv2.Canny(gray,50,250,apertureSize = 3)
# 进行霍夫直线运算
#lines = cv2.HoughLines(edges, 1, np.pi/180, 0)
lines = cv2.HoughLinesP(edges, 1, np.pi/180, 50, 100, minLineLength = 537, maxLineGap = 500)
print(len(lines))
# 对检测到的每一条线段
for line in lines:
    x1, y1, x2, y2 = line[0]
    cv2.line(edges, (x1, y1), (x2, y2), (255, 126, 255), 2)
#cv2.imshow("line_detection", edges)
#cv2.waitKey(0)
# 显示图像
#plt.subplot(2,1,1)
#plt.imshow(image_array,cmap=cm.gray)
#plt.axis("off")
#plt.subplot(2,1,2)
plt.imshow(image2,cmap=cm.gray)
#plt.axis("off")
plt.show()
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
