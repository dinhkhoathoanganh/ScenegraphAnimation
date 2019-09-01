import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;

import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import sgraph.IScenegraph;
import sgraph.TransformNode;
import util.Material;
import util.ObjectInstance;
import util.ShaderLocationsVault;
import util.ShaderProgram;

/**
 * Contains a camera's translation and rotation allowing for easy access to
 * the actual transformation of this camera.
 * Code relating to movement (translate method specifically) was originally inspired by:
 *   http://in2gpu.com/2016/02/26/opengl-fps-camera/
 */
public class Camera {
  private static final float SPHERE_SCALE = 1.5f;
  private static final float CYLINDER_RAD = .5f;
  private static final float CYLINDER_HEIGHT = 10f;
  private static final float CONE_RAD = 1;
  private static final float CONE_HEIGHT = 4;

  private TransformNode cameraNode;
  private Material sphereMat, xAxisMat, yAxisMat, zAxisMat;

  // Current rotation and translation
  private Matrix4f rotation;
  private Matrix4f translation;

  public Camera(TransformNode cameraNode) {
    this.translation = new Matrix4f();
    this.rotation = new Matrix4f();

    // Setup materials for objects
    this.sphereMat = new Material();
    this.sphereMat.setAmbient(1, 1, 1);
    this.xAxisMat = new Material();
    this.xAxisMat.setAmbient(1, 0, 0);
    this.yAxisMat = new Material();
    this.yAxisMat.setAmbient(0, 0, 0);
    this.zAxisMat = new Material();
    this.zAxisMat.setAmbient(0, 0, 1);

    this.cameraNode = cameraNode;
  }

  public void translate(Vector3f t) {
    translate(t.x, t.y, t.z);
  }

  public void translate(float x, float y, float z) {
    Matrix4f curTransform = getTransform();
    Vector3f xs = new Vector3f();
    Vector3f ys = new Vector3f();
    Vector3f zs = new Vector3f();

    // [ right_x  up_x  forward_x  x ]
    // [ right_y  up_y  forward_y  y ]
    // [ right_z  up_z  forward_z  z ]
    // [   0        0      0       1 ]
    // Get the x, y, and z components of the current right, up, and forward vectors
    curTransform.getRow(0, xs);
    curTransform.getRow(1, ys);
    curTransform.getRow(2, zs);

    // Multiply vectors by translation scalars
    xs.mul(-x);
    ys.mul(-y);
    zs.mul(-z);

    // Translate the current translation by the x, y, and z components, which have been converted
    // to the camera's coordinate system
    translation.translate(xs).translate(ys).translate(zs);

    setCamNode();
  }

  /**
   * Rotate by each provided angle (in degrees).
   * @param x x-degrees to rotate
   * @param y y-degrees to rotate
   * @param z z-degrees to rotate
   */
  public void rotate(float x, float y, float z) {
    // This is like the trackball rotation. Apply the new rotation to an identity matrix,
    // then multiply by the old rotation matrix.
    rotation = new Matrix4f()
            .rotateX((float) Math.toRadians(x))
            .rotateY((float) Math.toRadians(y))
            .rotateZ((float) Math.toRadians(z))
            .mul(this.rotation);

    setCamNode();
  }

  public Matrix4f getTransform() {
    return new Matrix4f().mul(rotation).mul(translation);
  }

  /**
   * Returns a float offset for where to actually place the camera so it is in front of the
   * sphere and arrows.
   */
  public float getCamOffset() {
//    return SPHERE_SCALE + CYLINDER_HEIGHT + CONE_HEIGHT + 0.5f;
    return .2f;
  }

  private void setCamNode() {
    if (cameraNode != null)
      cameraNode.setTransform(new Matrix4f().translate(0, 0, -getCamOffset()).mul(this.getTransform()).invert());
  }
}