import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.*;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import sgraph.INode;
import sgraph.IScenegraph;
import sgraph.SceneXMLReader;
import sgraph.TransformNode;
import util.ShaderProgram;

/**
 * Based on file provided by ashesh.
 *
 * The View class is the "controller" of all our OpenGL stuff. It cleanly
 * encapsulates all our OpenGL functionality from the rest of Java GUI, managed
 * by the JOGLFrame class.
 */
public class View {
  private static final float MAIN_WIN_DIM = 1.0f;
  private static final float SMALL_WIN_DIM = 0.4f;

  private int WINDOW_WIDTH, WINDOW_HEIGHT;
  private Stack<Matrix4f> modelView;
  private Matrix4f trackballTransform;
  private float trackballRadius;
  private Vector2f mousePos;
  private Matrix4f cameraProjection, droneProjection;
  private Matrix4f lookAtMatrix;

  private util.ShaderProgram program;
  private util.ShaderLocationsVault shaderLocations;
  private int projectionLocation;
  private sgraph.IScenegraph<VertexAttrib> scenegraph;

  private Vector3f lookAtEye;
  private Vector3f lookAtCenter;
  private Vector3f droneTranslation;
  private Vector3f droneRotation;
  private float startCamRot;
  private Camera droneCam;
  private TransformNode cameraNode;
  private float droneViewWidthRatio, droneViewHeightRatio;
  private float globalViewWidthRatio, globalViewHeightRatio;
  private long lastDrawTime;

  private float fovAngle = (float) Math.toRadians(100);

  private boolean isGlobalMain;

  /**
   * Create view using default eye and center.
   */
  public View() {
    this(new Vector3f(0, 0, 100), new Vector3f(0, 35, 0), 0,
            new Vector3f(), new Vector3f());
  }

  /**
   * Create view using passed eye, center, and rotation.
   * @param lookAtEye Eye position.
   * @param lookAtCenter Center position.
   * @param camRot Camera Y rotation.
   */
  public View(Vector3f lookAtEye, Vector3f lookAtCenter, float camRot,
              Vector3f droneTranslation, Vector3f droneRotation) {
    modelView = new Stack<Matrix4f>();
    trackballRadius = 300;
    trackballTransform = new Matrix4f();
    scenegraph = null;
    startCamRot = camRot;
    this.lookAtEye = lookAtEye;
    this.lookAtCenter = lookAtCenter;
    droneViewHeightRatio = droneViewWidthRatio = 1.0f;
    cameraProjection = new Matrix4f();
    droneProjection = new Matrix4f();
    globalViewHeightRatio = globalViewWidthRatio = .4f;
    isGlobalMain = false;
    lastDrawTime = -1;
    lookAtMatrix = new Matrix4f();
    this.droneTranslation = droneTranslation;
    this.droneRotation = droneRotation;
  }

  public void initScenegraph(GLAutoDrawable gla, InputStream in) throws Exception {
    GL3 gl = gla.getGL().getGL3();

    if (scenegraph != null)
      scenegraph.dispose();

    program.enable(gl);
    scenegraph = createSceneGraph(gla, program, in);
    program.disable(gl);
  }

  /**
   * Creates a Scenegraph given an InputStream and ShaderProgram. This should probably be moved
   * to a more "util" style class.
   */
  public static IScenegraph<VertexAttrib> createSceneGraph(GLAutoDrawable gla, ShaderProgram program,
                                                          InputStream in) throws Exception {
    IScenegraph<VertexAttrib> sg = SceneXMLReader.importScenegraph(in
            , new VertexAttribProducer());

    sgraph.IScenegraphRenderer renderer = new sgraph.GL3ScenegraphRenderer();
    renderer.setContext(gla);
    Map<String, String> shaderVarsToVertexAttribs = new HashMap<String, String>();
    shaderVarsToVertexAttribs.put("vPosition", "position");
    shaderVarsToVertexAttribs.put("vNormal", "normal");
    shaderVarsToVertexAttribs.put("vTexCoord", "texcoord");

    renderer.initShaderProgram(program, shaderVarsToVertexAttribs);

    System.out.println("Loading texture may take a while! Please wait...");

    sg.setRenderer(renderer);

    return sg;
  }

  public void initCam(GLAutoDrawable gla) {
    // Perform an initial lookAt to get where the camera should be. Apply the result translation
    // and rotation to the camera
    INode camNode = scenegraph.getNodes().get("camera");
    if (camNode != null && camNode instanceof TransformNode) {
      cameraNode = (TransformNode) camNode;
    }

    this.droneCam = new Camera(cameraNode);

    // Perform an initial lookAt to get where the camera should be. Apply the result translation
    // and rotation to the camera
    lookAtMatrix = new Matrix4f().lookAt(lookAtEye, lookAtCenter, new Vector3f(0, 1, 0));
    this.droneCam.translate(this.droneTranslation);
    this.droneCam.rotate(this.droneRotation.x, this.droneRotation.y, this.droneRotation.z);
  }

  public void init(GLAutoDrawable gla) throws Exception {
    GL3 gl = gla.getGL().getGL3();


    //compile and make our shader program. Look at the ShaderProgram class for details on how this is done
    program = new util.ShaderProgram();

    String shader = "phong-multiple";
    program.createProgram(gl, "shaders/" + shader + ".vert",
            "shaders/" + shader + ".frag");

    shaderLocations = program.getAllShaderVariables(gl);

    //get input variables that need to be given to the shader program
    projectionLocation = shaderLocations.getLocation("projection");
  }

  public void draw(GLAutoDrawable gla) {
    GL3 gl = gla.getGL().getGL3();

    gl.glClearColor(0f, 0f, 0f, 1);
    gl.glClear(gl.GL_COLOR_BUFFER_BIT | gl.GL_DEPTH_BUFFER_BIT);
    gl.glEnable(gl.GL_DEPTH_TEST);

    program.enable(gl);

    // Do scenegraph animation, ignoring the first call to avoid a large time step
    // for the first animation call
    long now = System.currentTimeMillis();
    if (lastDrawTime != -1) {
      float delta = now - lastDrawTime;
      scenegraph.animate(delta);
      lastDrawTime = now;
    } else {
      lastDrawTime = now;
    }

    // Switch which view is drawn first based on which is the main view.
    // This eliminates one view drawing "in" the other when objects overlap.
    if (isGlobalMain) {
      drawGlobalView(gla);
      drawDroneView(gla);
    } else {
      drawDroneView(gla);
      drawGlobalView(gla);
    }

    gl.glFlush();
    program.disable(gl);
  }

  private void drawGlobalView(GLAutoDrawable gla) {
    GL3 gl = gla.getGL().getGL3();
    // Clear the depth buffer. This allows for correct layering when drawing multiple viewports
    gl.glClear(gl.GL_DEPTH_BUFFER_BIT);

    while (!modelView.empty())
      modelView.pop();

    // Setup modelView to be the correct camera modelView
    modelView.push(new Matrix4f(lookAtMatrix));
    modelView.peek();
            // Put this back to re-enable trackball
            //.mul(trackballTransform);

    FloatBuffer fb = Buffers.newDirectFloatBuffer(16);
    gl.glUniformMatrix4fv(projectionLocation, 1, false, cameraProjection.get(fb));

    // Set the viewport
    gl.glViewport((int) (WINDOW_WIDTH * (1.0f - globalViewWidthRatio)),
            (int) (WINDOW_HEIGHT * (1.0 - globalViewHeightRatio)),
            (int) (WINDOW_WIDTH * globalViewWidthRatio),
            (int) (WINDOW_HEIGHT * globalViewHeightRatio));

    // Draw the scene
    scenegraph.draw(modelView);

    // Draw camera object
//    this.droneCam.draw(gla, new Matrix4f(lookAtMatrix).mul(this.droneCam.getTransform().invert()));
  }

  private void drawDroneView(GLAutoDrawable gla) {
    GL3 gl = gla.getGL().getGL3();
    gl.glClear(gl.GL_DEPTH_BUFFER_BIT);

    while (!modelView.empty())
      modelView.pop();

    // Setup modelView to be the correct camera modelView
    modelView.push(new Matrix4f());
    modelView.peek().mul(this.droneCam.getTransform());
            // Put this back to re-enable trackball
            //.mul(trackballTransform);

    FloatBuffer fb = Buffers.newDirectFloatBuffer(16);
    gl.glUniformMatrix4fv(projectionLocation, 1, false, droneProjection.get(fb));

    // Set the viewport
    gl.glViewport((int) (WINDOW_WIDTH * (1.0f - droneViewWidthRatio)),
            (int) (WINDOW_HEIGHT * (1.0 - droneViewHeightRatio)),
            (int) (WINDOW_WIDTH * droneViewWidthRatio),
            (int) (WINDOW_HEIGHT * droneViewHeightRatio));

    // Draw the scene
    scenegraph.draw(modelView);
  }

  public void mousePressed(int x, int y) {
    mousePos = new Vector2f(x, y);
  }

  public void mouseReleased(int x, int y) {  }

  public void mouseDragged(int x, int y) {
    Vector2f newM = new Vector2f(x, y);

    Vector2f delta = new Vector2f(newM.x - mousePos.x, newM.y - mousePos.y);
    mousePos = new Vector2f(newM);

    trackballTransform = new Matrix4f().rotate(delta.x / trackballRadius, 0, 1, 0)
            .rotate(delta.y / trackballRadius, 1, 0, 0)
            .mul(trackballTransform);
  }

  public void reshape(GLAutoDrawable gla, int x, int y, int width, int height) {
    GL gl = gla.getGL();
    WINDOW_WIDTH = width;
    WINDOW_HEIGHT = height;

    // managing view for global and drone camera
    cameraProjection = new Matrix4f().perspective((float) Math.toRadians(100),
            (WINDOW_WIDTH * droneViewWidthRatio) / (WINDOW_HEIGHT * droneViewHeightRatio), 0.1f, 10000.0f);
    droneProjection = new Matrix4f().perspective(this.fovAngle,
              (WINDOW_WIDTH * droneViewWidthRatio) / (WINDOW_HEIGHT * droneViewHeightRatio), 0.1f, 10000.0f);
//    droneProjection = new Matrix4f().ortho(-WINDOW_WIDTH / 8f, WINDOW_WIDTH / 8f, -WINDOW_HEIGHT / 8f, WINDOW_HEIGHT / 8f,
//            0.1f, 10000f);

  }

  public void dispose(GLAutoDrawable gla) {
    this.scenegraph.dispose();
  }

  //Utility functions for drone camera
  public void moveCam(float x, float y, float z) {
    this.droneCam.translate(x, y, z);
  }

  public void rotateCam(float x, float y, float z) {
    this.droneCam.rotate(x, y, z);
  }

  public void zoomCam(Boolean add){
    //change FOV
    if (add) {
      if (this.fovAngle<=Math.PI-0.2) {
        this.fovAngle += 0.1;
      }
    } else {
      if (this.fovAngle>=0.2) {
        this.fovAngle -= 0.1;
      }
    }

    // change FOV for camera
    droneProjection = new Matrix4f().perspective(this.fovAngle,
              (WINDOW_WIDTH * droneViewWidthRatio) / (WINDOW_HEIGHT * droneViewHeightRatio), 0.1f, 10000.0f);

  }
  public void switchMainCam() {
    isGlobalMain = !isGlobalMain;

    if (isGlobalMain) {
      globalViewHeightRatio = globalViewWidthRatio = MAIN_WIN_DIM;
      droneViewHeightRatio = droneViewWidthRatio = SMALL_WIN_DIM;
    } else {
      globalViewHeightRatio = globalViewWidthRatio = SMALL_WIN_DIM;
      droneViewHeightRatio = droneViewWidthRatio = MAIN_WIN_DIM;
    }
  }
}
