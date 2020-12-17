#define OPENCV_TRAITS_ENABLE_DEPRECATED

#include <android/asset_manager_jni.h>
#include "jni_long_field.h"
#include "live/live.h"
#include "android_log.h"
#include "img_process.h"
#include <opencv2/opencv.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <dlib/opencv.h>
#include <dlib/image_processing/frontal_face_detector.h>
#include <dlib/image_processing/render_face_detections.h>
#include <dlib/image_processing/shape_predictor.h>

#include "opencv2/core.hpp"
#include "opencv2/features2d.hpp"
#include "opencv2/core/affine.hpp"
#include "opencv2/calib3d/calib3d.hpp"

#include <dlib/dnn.h>
#include <dlib/gui_widgets.h>
#include <dlib/clustering.h>
#include <dlib/string.h>
#include <dlib/image_io.h>
#include <android/asset_manager_jni.h>

using namespace dlib;
using namespace std;
using namespace cv;

struct membuf : std::streambuf {
    membuf(char* begin, char* end) {
        this->setg(begin, begin, end);
    }
};
JniLongField live_field("nativeHandler");

Live* get_live(JNIEnv* env, jobject instance) {
    Live* const live = reinterpret_cast<Live*>(live_field.get(env, instance));
    return live;
}

void set_live(JNIEnv* env, jobject instance, Live* live) {
    live_field.set(env, instance, reinterpret_cast<intptr_t>(live));
}

//======================================================
template <template <int,template<typename>class,int,typename> class block, int N, template<typename>class BN, typename SUBNET>
using residual = add_prev1<block<N,BN,1,tag1<SUBNET>>>;

template <template <int,template<typename>class,int,typename> class block, int N, template<typename>class BN, typename SUBNET>
using residual_down = add_prev2<avg_pool<2,2,2,2,skip1<tag2<block<N,BN,2,tag1<SUBNET>>>>>>;

template <int N, template <typename> class BN, int stride, typename SUBNET>
using block  = BN<con<N,3,3,1,1,relu<BN<con<N,3,3,stride,stride,SUBNET>>>>>;

template <int N, typename SUBNET> using ares      = relu<residual<block,N,affine,SUBNET>>;
template <int N, typename SUBNET> using ares_down = relu<residual_down<block,N,affine,SUBNET>>;

template <typename SUBNET> using alevel0 = ares_down<256,SUBNET>;
template <typename SUBNET> using alevel1 = ares<256,ares<256,ares_down<256,SUBNET>>>;
template <typename SUBNET> using alevel2 = ares<128,ares<128,ares_down<128,SUBNET>>>;
template <typename SUBNET> using alevel3 = ares<64,ares<64,ares<64,ares_down<64,SUBNET>>>>;
template <typename SUBNET> using alevel4 = ares<32,ares<32,ares<32,SUBNET>>>;

using anet_type = loss_metric<fc_no_bias<128,avg_pool_everything<
        alevel0<
                alevel1<
                        alevel2<
                                alevel3<
                                        alevel4<
                                                max_pool<3,3,2,2,relu<affine<con<32,7,7,2,2,
                                                        input_rgb_image_sized<150>
                                                >>>>>>>>>>>>;

// ----------------------------------------------------------------------------------------

std::vector<matrix<rgb_pixel>> jitter_image(
        const matrix<rgb_pixel>& img
);

// ----------------------------------------------------------------------------------------




extern "C" {

JNIEXPORT jlong JNICALL
LIVE_METHOD(allocate)(JNIEnv *env, jobject instance);

JNIEXPORT void JNICALL
LIVE_METHOD(deallocate)(JNIEnv *env, jobject instance);


JNIEXPORT jint JNICALL
LIVE_METHOD(nativeLoadModel)(JNIEnv *env, jobject instance, jobject asset_manager,
                             jobject configs);

JNIEXPORT jfloat JNICALL
LIVE_METHOD(nativeDetectYuv)(JNIEnv *env, jobject instance, jbyteArray yuv, jint preview_width,
                             jint preview_height, jint orientation, jint left, jint top, jint right, jint bottom);

}


void ConvertAndroidConfig2NativeConfig(JNIEnv *env,jobject model_configs, std::vector<ModelConfig>& modelConfigs) {
    modelConfigs.clear();

    jclass list_clz = env->GetObjectClass(model_configs);
    jmethodID list_size = env->GetMethodID(list_clz, "size", "()I");
    jmethodID list_get = env->GetMethodID(list_clz, "get", "(I)Ljava/lang/Object;");

    env->DeleteLocalRef(list_clz);

    int len = env->CallIntMethod(model_configs, list_size);
    for(int i = 0; i < len; i++) {
        jobject config = env->CallObjectMethod(model_configs, list_get, i);
        jclass config_clz = env->GetObjectClass(config);
        jfieldID config_name         = env->GetFieldID(config_clz, "name" ,"Ljava/lang/String;");
        jfieldID config_width        = env->GetFieldID(config_clz, "width", "I");
        jfieldID config_height       = env->GetFieldID(config_clz, "height", "I");
        jfieldID config_scale        = env->GetFieldID(config_clz, "scale", "F");
        jfieldID config_shift_x      = env->GetFieldID(config_clz, "shift_x", "F");
        jfieldID config_shift_y      = env->GetFieldID(config_clz, "shift_y", "F");
        jfieldID config_org_resize   = env->GetFieldID(config_clz, "org_resize", "Z");

        env->DeleteLocalRef(config_clz);

        ModelConfig modelConfig;
        modelConfig.width       = env->GetIntField(config, config_width);
        modelConfig.height      = env->GetIntField(config, config_height);
        modelConfig.scale       = env->GetFloatField(config, config_scale);
        modelConfig.shift_x     = env->GetFloatField(config, config_shift_x);
        modelConfig.shift_y     = env->GetFloatField(config, config_shift_y);
        modelConfig.org_resize  = env->GetBooleanField(config, config_org_resize);
        jstring model_name_jstr   = static_cast<jstring>(env->GetObjectField(config, config_name));
        const char *name = env->GetStringUTFChars(model_name_jstr, 0);

        std::string nameStr(name);
        modelConfig.name = nameStr;
        modelConfigs.push_back(modelConfig);

        env->ReleaseStringUTFChars(model_name_jstr, name);

    }
}


JNIEXPORT jlong JNICALL
LIVE_METHOD(allocate)(JNIEnv *env, jobject instance) {
    auto * const live = new Live();
    set_live(env, instance, live);
    return reinterpret_cast<intptr_t> (live);
}


JNIEXPORT void JNICALL
LIVE_METHOD(deallocate)(JNIEnv *env, jobject instance) {
    delete get_live(env, instance);
    set_live(env, instance, nullptr);
}

JNIEXPORT jint JNICALL
LIVE_METHOD(nativeLoadModel)(JNIEnv *env, jobject instance, jobject asset_manager,
        jobject configs) {
    std::vector<ModelConfig> model_configs;
    ConvertAndroidConfig2NativeConfig(env, configs, model_configs);

    AAssetManager* mgr = AAssetManager_fromJava(env, asset_manager);
    return get_live(env, instance)->LoadModel(mgr, model_configs);
}


JNIEXPORT jfloat JNICALL
LIVE_METHOD(nativeDetectYuv)(JNIEnv *env, jobject instance, jbyteArray yuv, jint preview_width,
        jint preview_height, jint orientation, jint left, jint top, jint right, jint bottom) {
    jbyte *yuv_ = env->GetByteArrayElements(yuv, nullptr);

    cv::Mat bgr;
    Yuv420sp2bgr(reinterpret_cast<unsigned char *>(yuv_), preview_width, preview_height, orientation, bgr);

    FaceBox faceBox;
    faceBox.x1 = left;
    faceBox.y1 = top;
    faceBox.x2 = right;
    faceBox.y2 = bottom;

    float confidence = get_live(env, instance)->Detect(bgr, faceBox);
    env->ReleaseByteArrayElements(yuv, yuv_, 0);
    return confidence;
}

dlib::frontal_face_detector detector;
dlib::shape_predictor sp;
std::vector<dlib::rectangle> faceNums;
std::vector<full_object_detection> shapes;
std::vector<cv::Point3d> model_points;
anet_type net;

extern "C" JNIEXPORT jint JNICALL
Java_com_mv_engine_Live_initDlibLandMarks(JNIEnv *env, jobject thiz, jobject asset_manager,
                                          jstring file_name) {
    detector = dlib::get_frontal_face_detector();

    AAssetManager* mgr = AAssetManager_fromJava(env, asset_manager);
    if(mgr==NULL)
    {
        return 11;
    }
    /*获取文件名并打开*/
    jboolean iscopy;
    const char *mfile = env->GetStringUTFChars(file_name, &iscopy);
    AAsset* asset = AAssetManager_open(mgr, mfile,AASSET_MODE_UNKNOWN);
    env->ReleaseStringUTFChars(file_name, mfile);
    if(asset==NULL)
    {
        return 22;
    }

    auto file_length = static_cast<size_t>(AAsset_getLength(asset));
    if (file_length == 0){
        return 33;
    }
    char *model_buffer = (char *) malloc(file_length);
    //read file data
    AAsset_read(asset, model_buffer, file_length);
    //the data has been copied to model_buffer, so , close it
    AAsset_close(asset);

    //char* to istream
    membuf mem_buf(model_buffer, model_buffer + file_length);
    std::istream in(&mem_buf);

    //load shape_predictor_68_face_landmarks.dat from memory
    dlib::deserialize(sp,in);
    //free malloc
    free(model_buffer);
    // 3D model points.
    model_points.push_back(cv::Point3d(0.0f, 0.0f, 0.0f));               // 鼻尖
    model_points.push_back(cv::Point3d(0.0f, -330.0f, -65.0f));          // 下巴
    model_points.push_back(cv::Point3d(-225.0f, 170.0f, -135.0f));       // 左眼左上角
    model_points.push_back(cv::Point3d(225.0f, 170.0f, -135.0f));        // 右眼右上角
    model_points.push_back(cv::Point3d(-150.0f, -150.0f, -125.0f));      // 左嘴角
    model_points.push_back(cv::Point3d(150.0f, -150.0f, -125.0f));       // 右嘴角
    return file_length;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mv_engine_Live_initDlib128Vector(JNIEnv *env, jobject thiz, jobject asset_manager, jstring file_name) {
    AAssetManager* mgr = AAssetManager_fromJava(env, asset_manager);
    if(mgr==NULL)
    {
        return 11;
    }
    /*获取文件名并打开*/
    jboolean iscopy;
    const char *mfile = env->GetStringUTFChars(file_name, &iscopy);
    AAsset* asset = AAssetManager_open(mgr, mfile,AASSET_MODE_UNKNOWN);
    env->ReleaseStringUTFChars(file_name, mfile);
    if(asset==NULL)
    {
        return 22;
    }

    auto file_length = static_cast<size_t>(AAsset_getLength(asset));
    if (file_length == NULL){
        return 33;
    }
    char *model_buffer = (char *) malloc(file_length);
    //读取文件数据
    AAsset_read(asset, model_buffer, file_length);
    //数据已复制到model_buffer，所以，将其关闭
    AAsset_close(asset);

    //char* to istream
    membuf mem_buf(model_buffer, model_buffer + file_length);
    std::istream in(&mem_buf);

    //从内存中加载shape_predictor_68_face_landmarks.dat
    dlib::deserialize(net,in);
    //释放malloc
    free(model_buffer);
    return file_length;
}

Mat eulerAnglesToRotationMatrix(Vec3f &theta) {
    // 计算绕x轴的旋转
    Mat R_x = (Mat_<double>(3, 3) <<
                                  1, 0, 0,
            0, cos(theta[0]), -sin(theta[0]),
            0, sin(theta[0]), cos(theta[0])
    );
    // 计算绕y轴的旋转
    Mat R_y = (Mat_<double>(3, 3) <<
                                  cos(theta[1]), 0, sin(theta[1]),
            0, 1, 0,
            -sin(theta[1]), 0, cos(theta[1])
    );
    // 计算绕z轴的旋转
    Mat R_z = (Mat_<double>(3, 3) <<
                                  cos(theta[2]), -sin(theta[2]), 0,
            sin(theta[2]), cos(theta[2]), 0,
            0, 0, 1
    );
    // 组合旋转矩阵
    Mat R = R_z * R_y * R_x;
    return R;
}

Vec3f rotationMatrixToEulerAngles(Mat &R) {
    float sy = sqrt(
            R.at<double>(0, 0) * R.at<double>(0, 0) + R.at<double>(1, 0) * R.at<double>(1, 0));
    bool singular = sy < 1e-6; // If
    float x, y, z;
    if (!singular) {
        x = atan2(R.at<double>(2, 1), R.at<double>(2, 2));
        y = atan2(-R.at<double>(2, 0), sy);
        z = atan2(R.at<double>(1, 0), R.at<double>(0, 0));
    } else {
        x = atan2(-R.at<double>(1, 2), R.at<double>(1, 1));
        y = atan2(-R.at<double>(2, 0), sy);
        z = 0;
    }
#if 1
    x = x * 180.0f / 3.141592653589793f;
    y = y * 180.0f / 3.141592653589793f;
    z = z * 180.0f / 3.141592653589793f;
#endif

    return Vec3f(x, y, z);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_mv_engine_FaceAngleDet_faceAngleDet(JNIEnv *env, jclass clazz, jlong mat_addr) {

    Mat &matImg = *(Mat *) mat_addr;
//    cv::cvtColor(matImg,matImg,CV_RGB2GRAY);
    array2d<unsigned char> img;
    dlib::assign_image(img, dlib::cv_image<unsigned char>(matImg));
    faceNums = detector(img);

    if  (faceNums.size() != 0){
        int ratio = int(img.nc()) / int(matImg.cols);
        std::vector<cv::Point2d> landmarks;
        shapes.clear();
        for (unsigned long j = 0; j < faceNums.size(); ++j) {
            full_object_detection shape = sp(img, faceNums[j]);
            //如果需要的话，可以获取所有面部部位的位置。在这里，我们只是将它们存储为形状，以便将它们放在屏幕上。
            shapes.push_back(shape);
            // 2D image points.
            landmarks.push_back( cv::Point2d(int(shape.part(30).x() / ratio), int(shape.part(30).y() / ratio)) );    // 鼻尖
            landmarks.push_back( cv::Point2d(int(shape.part( 8).x() / ratio), int(shape.part( 8).y() / ratio)) );    // 下巴
            landmarks.push_back( cv::Point2d(int(shape.part(36).x() / ratio), int(shape.part(36).y() / ratio)) );     // 左眼左上角
            landmarks.push_back( cv::Point2d(int(shape.part(45).x() / ratio), int(shape.part(45).y() / ratio)) );    // 右眼右上角
            landmarks.push_back( cv::Point2d(int(shape.part(48).x() / ratio), int(shape.part(48).y() / ratio)) );    // 左嘴角
            landmarks.push_back( cv::Point2d(int(shape.part(54).x() / ratio), int(shape.part(54).y() / ratio)) );    // 右嘴角
        }

        double focal_length = matImg.cols; // 近似焦距。
        cv::Point2d center = cv::Point2d(matImg.cols / 2, matImg.rows / 2);
        cv::Mat camera_matrix = (cv::Mat_<double>(3, 3) << focal_length, 0, center.x, 0, focal_length, center.y, 0, 0, 1);
        cv::Mat dist_coeffs = cv::Mat::zeros(4, 1, cv::DataType<double>::type); // 假设没有镜头变形

        Vec3f rotation_vector; // 轴角旋转
        cv::Mat translation_vector;
        // 算出旋转矩阵
        cv::solvePnP(model_points, landmarks, camera_matrix, dist_coeffs, rotation_vector, translation_vector);
        Mat rotationMatrix = eulerAnglesToRotationMatrix(rotation_vector);
        const Vec3f &angle = rotationMatrixToEulerAngles(rotationMatrix);
        int angleValue[3];
        angleValue[0] = angle[0];
        angleValue[1] = angle[1];
        angleValue[2] = angle[2];
        jclass intClass = env->FindClass("[I");
        jintArray intArray = env->NewIntArray(3);
        env->SetIntArrayRegion(intArray,0,3, angleValue);
        return intArray;
    }else{
        int x[3] = {0,0,0};
        jintArray intArray = env->NewIntArray(3);
        env->SetIntArrayRegion(intArray,0,3, x);
        return intArray;
    }
}


extern "C"
JNIEXPORT jdouble JNICALL
Java_com_mv_engine_Live_stdDet(JNIEnv *env, jobject thiz, jlong mat_addr) {

    Mat &matImg = *(Mat *) mat_addr;
    cv::rotate(matImg, matImg,ROTATE_90_CLOCKWISE); // OpenCV本身的问题，造成获取的图像旋转了90°，而dlib只能识别竖直的人头，所以我们需要逆时针旋转回来
    Mat imageLaplacian;
//    Laplacian(cmatImg, imageSobel, CV_16U);
    Laplacian(matImg, imageLaplacian, CV_64F);
    //Sobel(imageGrey, imageSobel, CV_16U, 1, 1);
    //图像的平均灰度
//    double meanValue = mean(imageLaplacian)[0];
    Mat mat_mean, mat_stddev;
    meanStdDev(imageLaplacian,mat_mean,mat_stddev);
//    double m;
    double s;
    //灰度值
//    m = mat_mean.at<double>(0,0);
    //标准差
    s = mat_stddev.at<double>(0, 0);
    return s;
}

extern "C"
JNIEXPORT jdoubleArray JNICALL
Java_com_mv_engine_Live_face128VectorDet(JNIEnv *env, jobject thiz, jlong mat_addr) {

    detector = dlib::get_frontal_face_detector();

    Mat &matImg = *(Mat *) mat_addr;
    Mat cmatImg = matImg.clone();
    array2d<unsigned char> img;
    dlib::assign_image(img, dlib::cv_image<unsigned char>(cmatImg));
    faceNums = detector(img);
    std::vector<full_object_detection> shapes;
    std::vector< matrix<rgb_pixel> > faces;
    if (!faceNums.empty()){
        for (unsigned long j = 0; j < faceNums.size(); ++j)
        {
            full_object_detection shape = sp(img, faceNums[j]);
            matrix<rgb_pixel> face_chip;
            extract_image_chip(img , get_face_chip_details(shape , 150 , 0.25) , face_chip);
            faces.push_back(move(face_chip));
            //如果需要的话，可以获取所有面部部位的位置。在这里，我们只是将它们存储为形状，以便将它们放在屏幕上。
            shapes.push_back(shape);
        }
        ////将150*150人脸图像载入Resnet残差网络，返回128D人脸特征存于face_descriptors
        std::vector<matrix<float,0,1>> face_descriptors = net(faces);
//        return (double)length(face_descriptors[0]);
//        return 1;
        double vector128[128];
        for (int i = 0; i < 128 ; ++i) {
            vector128[i] = face_descriptors[0].operator()(i);
        }
        jdoubleArray doubleArray = env->NewDoubleArray(128);
        env->SetDoubleArrayRegion(doubleArray,0,128,vector128);
        return doubleArray;
    } else{
        return nullptr;
    }
}