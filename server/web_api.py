# -*- coding:utf-8 -*-
from flask import Flask, jsonify, abort, make_response, request, url_for,render_template
from flask_httpauth import HTTPBasicAuth
import json

import os
import ntpath
import argparse

import face_mysql

import numpy as np
from scipy import misc
import matrix_fun

import urllib
import face_recognition
import cv2
import datetime
import time

import image_warp

app = Flask(__name__)
# 图片最大为16M
app.config['MAX_CONTENT_LENGTH'] = 16 * 1024 * 1024
auth = HTTPBasicAuth()

#设置最大的相似距离，1.22
MAX_DISTINCT=1.22

# 设置上传的图片路径和格式
# from werkzeug import secure_filename
from werkzeug.utils import secure_filename
#设置post请求中获取的图片保存的路径
UPLOAD_FOLDER = 'pic_tmp/'
ROCK_FOLDER = 'rock_temp/'
if not os.path.exists(UPLOAD_FOLDER):
    os.makedirs(UPLOAD_FOLDER)
else:
    pass
ALLOWED_EXTENSIONS = set(['png', 'jpg', 'jpeg'])
app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER
app.config['ROCK_FOLDER'] = ROCK_FOLDER


def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1] in ALLOWED_EXTENSIONS


#当前文件的路径
pwd = os.getcwd()

@app.route('/')
def index():
    return render_template("face_login.html")

@app.route('/face_insert_html')
def face_insert_html():
    return render_template("face_insert.html")
@app.route('/face_query_html')
def face_query_html():
    return render_template("face_login.html")

@app.route('/rock_correct')
def rock_correct_html():
    return render_template("rock_correct.html")
@app.route('/rock_split')
def rock_split_html():
    return render_template("rock_split.html")

#获取post中的图片并执行插入到库 返回数据库中保存的id
@app.route('/face/insert', methods=['POST'])
def face_insert():
    #分别获取post请求中的uid 和ugroup作为图片信息
    uid = request.form['uid']
    ugroup = request.form['ugroup']
    upload_files = request.files['imagefile']

    #从post请求图片保存到本地路径中
    file = upload_files
    if file and allowed_file(file.filename):
        filename = datetime.datetime.now().strftime("%Y-%m-%d-%H-%M-%S-") + secure_filename(file.filename)
        file.save(os.path.join(app.config['UPLOAD_FOLDER'], filename))
    image_path = os.path.join(app.config['UPLOAD_FOLDER'], filename)
    image_path = os.path.join(pwd, image_path)
    print(image_path)

    #img = cv2.imread(image_path)
    image = face_recognition.load_image_file(image_path)
    face_locations = face_recognition.face_locations(image)
    emb_array = face_recognition.face_encodings(image, face_locations)
    filename_base, file_extension = os.path.splitext(filename)
    id_list = []
    #存入数据库
    for j in range(0, len(emb_array)):
        face_mysql_instant = face_mysql.face_mysql()
        last_id = face_mysql_instant.insert_facejson(filename_base + "_" + str(j), image_path, 
                                                    ",".join(str(li) for li in emb_array[j].tolist()), uid, ugroup)
        id_list.append(str(last_id))

    #设置返回类型
    request_result = {}
    request_result['id'] = ",".join(id_list)
    if len(id_list) > 0:
        request_result['state'] = 'sucess'
    else:
        request_result['state'] = 'error'
        os.remove(image_path)

    print(request_result)
    return json.dumps(request_result)


@app.route('/face/query', methods=['POST'])
def face_query():
    #获取查询条件  在ugroup中查找相似的人脸
    ugroup = request.form['ugroup']
    upload_files = request.files['imagefile']

    #设置返回结果
    result = []

    #获取post请求的图片到本地
    file = upload_files
    if file and allowed_file(file.filename):
        filename = datetime.datetime.now().strftime("%Y-%m-%d-%H-%M-%S-") + secure_filename(file.filename)
        file.save(os.path.join(app.config['UPLOAD_FOLDER'], filename))
        image_path = os.path.join(app.config['UPLOAD_FOLDER'], filename)
        image_path = os.path.join(pwd, image_path)
        print(image_path)

        #img = cv2.imread(image_path)
        image = face_recognition.load_image_file(image_path)
        face_locations = face_recognition.face_locations(image)
        emb_array = face_recognition.face_encodings(image, face_locations)
        os.remove(image_path)
        #if emb_array.shape[0] == 1:
        #    return json.dumps({'error': "not found face"})
        face_query = matrix_fun.matrix()
        #分别获取距离该图片中人脸最相近的人脸信息
        # pic_min_scores 是数据库中人脸距离（facenet计算人脸相似度根据人脸距离进行的）
        # pic_min_names 是当时入库时保存的文件名
        # pic_min_uid  是对应的用户id
        pic_min_scores, pic_min_names, pic_min_uid = face_query.get_socres(np.array(emb_array), ugroup)
        #如果提交的query没有group 则返回
        if len(pic_min_scores) == 0:
            return json.dumps({'error': "not found user group"})

        for i in range(0, len(pic_min_scores)):
            if pic_min_scores[i]<MAX_DISTINCT:
                rdict = {'uid': pic_min_uid[i],
                        'distance': pic_min_scores[i],
                        'pic_name': pic_min_names[i] }
                result.append(rdict)
        print(result)
    if len(result)==0 :
        return json.dumps({"state":"success, but not match face"})
    else:
        return json.dumps(result)

@app.route('/rock/correct', methods=['POST'])
def rock_correct():
    upload_files = request.files['imagefile']
    #从post请求图片保存到本地路径中
    file = upload_files
    if file and allowed_file(file.filename):
        filename = datetime.datetime.now().strftime("%Y-%m-%d-%H-%M-%S-") + secure_filename(file.filename)
        file.save(os.path.join(app.config['ROCK_FOLDER'], filename))
    image_path = os.path.join(app.config['ROCK_FOLDER'], filename)
    image_path = os.path.join(pwd, image_path)
    dst_image_path = "dst_" + filename
    dst_image_path = os.path.join(app.config['ROCK_FOLDER'], dst_image_path)
    dst_image_path = os.path.join(pwd, dst_image_path)
    print(image_path)
    print(dst_image_path)
    image_warp.image_warp(image_path, dst_image_path)
    image_data = open(dst_image_path, "rb").read()
    os.remove(image_path)
    os.remove(dst_image_path)
    response = make_response(image_data)
    response.headers['Content-Type'] = 'image/png'
    return response

@app.route('/rock/split', methods=['POST'])
def rock_split():
    upload_files = request.files.getlist('imagefile')
    #从post请求图片保存到本地路径中
    filenames = []
    for file in upload_files:
        if file and allowed_file(file.filename):
            filename = datetime.datetime.now().strftime("%Y-%m-%d-%H-%M-%S-") + secure_filename(file.filename)
            file.save(os.path.join(app.config['ROCK_FOLDER'], filename))
            image_path = os.path.join(app.config['ROCK_FOLDER'], filename)
            image_path = os.path.join(pwd, image_path)
            filenames.append(image_path)
    dst_image_path = "dst_"+ datetime.datetime.now().strftime("%Y-%m-%d-%H-%M-%S-") + "split.jpg"
    image_path = os.path.join(app.config['ROCK_FOLDER'], dst_image_path)
    image_path = os.path.join(pwd, image_path)
    print(image_path)
    image_warp.image_split(filenames, dst_image_path)
    image_data = open(dst_image_path, "rb").read()
    os.remove(dst_image_path)
    for filename in filenames:
        os.remove(filename)
    response = make_response(image_data)
    response.headers['Content-Type'] = 'image/png'
    return response

# 备用 通过urllib的方式从远程地址获取一个图片到本地
# 利用该方法可以提交一个图片的url地址，则也是先保存到本地再进行后续处理
def get_url_imgae(picurl):
    response = urllib.urlopen(picurl)
    pic = response.read()
    pic_name = "pic_tmp/" + os.path.basename(picurl)
    with open(pic_name, 'wb') as f:
        f.write(pic)
    return pic_name

@auth.get_password
def get_password(username):
    if username == 'root':
        return 'root'
    return None

@auth.error_handler
def unauthorized():
    return make_response(jsonify({'error': 'Unauthorized access'}), 401)

@app.errorhandler(400)
def not_found(error):
    return make_response(jsonify({'error': 'Invalid data!'}), 400)

if __name__ == '__main__':
    app.run(host='127.0.0.1', port=8080)
