package cordova.plugin.facespoofing;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Vector;

import cordova.plugin.facespoofing.mtcnn.Align;
import cordova.plugin.facespoofing.mtcnn.Box;
import cordova.plugin.facespoofing.mtcnn.MTCNN;

public class FaceSpoofingProcess extends Activity {

  private FaceAntiSpoofing fas;
  private MTCNN mtcnn;
  private Boolean liveness = false;
  private Boolean error = false;
  private String message;
  private String score;
  private String threshold;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Bundle data = getIntent().getExtras();
    String images = data.getString("image", "");
    byte[] decodedString = Base64.decode(images, Base64.DEFAULT);
    Bitmap mBitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

    try {
      mtcnn = new MTCNN(getAssets());
      fas = new FaceAntiSpoofing(getAssets());
    } catch (IOException e) {
      e.printStackTrace();
    }

    this.antiSpoofing(mBitmap);
  }

  private void antiSpoofing(Bitmap mBitmap) {
    if (mBitmap == null) {
      this.error = true;
      this.message = "Harap deteksi wajah terlebih dahulu";
      this.response();
      return;
    }

    Bitmap bitmapTemp1 = mBitmap.copy(mBitmap.getConfig(), false);
    Vector<Box> boxes1 = mtcnn.detectFaces(bitmapTemp1, bitmapTemp1.getWidth() / 5);

    if (boxes1.size() == 0) {
      this.error = true;
      this.message = "Tidak ada wajah yang terdeteksi";
      this.response();
      return;
    }

    Box box1 = boxes1.get(0);
    bitmapTemp1 = Align.face_align(bitmapTemp1, box1.landmark);
    boxes1 = mtcnn.detectFaces(bitmapTemp1, bitmapTemp1.getWidth() / 5);

    if (boxes1.size() == 0) {
      this.error = true;
      this.message = "Tidak ada wajah yang terdeteksi";
      this.response();
      return;
    }

    box1 = boxes1.get(0);
    box1.toSquareShape();
    box1.limitSquare(bitmapTemp1.getWidth(), bitmapTemp1.getHeight());
    Rect rect1 = box1.transform2Rect();
    Bitmap bitmapCrop1 = MyUtil.crop(bitmapTemp1, rect1);

    int laplace2 = fas.laplacian(bitmapCrop1);

    if (laplace2 < FaceAntiSpoofing.LAPLACIAN_THRESHOLD) {
      this.error = false;
      this.score = "" + laplace2;
      this.threshold = "" + FaceAntiSpoofing.LAPLACIAN_THRESHOLD;
      this.liveness = false;
    } else {
      float score2 = fas.antiSpoofing(bitmapCrop1);
      if (score2 < FaceAntiSpoofing.THRESHOLD) {
        this.error = false;
        this.score = "" + score2;
        this.threshold = "" + FaceAntiSpoofing.THRESHOLD;
        this.liveness = true;
      } else {
        this.error = false;
        this.score = "" + score2;
        this.threshold = "" + FaceAntiSpoofing.THRESHOLD;
        this.liveness = false;
      }
    }

    this.response();
  }

  private void response() {
    JSONObject obj = new JSONObject();
    try {
      obj.put("error", this.error);
      obj.put("score", this.score);
      obj.put("threshold", this.threshold);
      obj.put("liveness", this.liveness);
      obj.put("message", this.message);
    } catch (JSONException e) {
      e.printStackTrace();
      obj = new JSONObject();
    }

    Intent data = new Intent();
    data.putExtra("data", obj.toString());
    setResult(RESULT_OK, data);
    finish();
  }

}
