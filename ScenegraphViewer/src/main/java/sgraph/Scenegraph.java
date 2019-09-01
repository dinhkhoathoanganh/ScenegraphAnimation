package sgraph;

import org.joml.Matrix4f;
import util.IVertexData;
import util.Light;
import util.PolygonMesh;

import java.util.*;

/**
 * A specific implementation of this scene graph. This implementation is still independent
 * of the rendering technology (i.e. OpenGL)
 * @author Amit Shesh
 */
public class Scenegraph<VertexType extends IVertexData> implements IScenegraph<VertexType>
{
  /**
   * The root of the scene graph tree
   */
  protected INode root;

  /**
   * A map to store the (name,mesh) pairs. A map is chosen for efficient search
   */
  protected Map<String,util.PolygonMesh<VertexType>> meshes;

  /**
   * A map to store the (name,node) pairs. A map is chosen for efficient search
   */
  protected Map<String,INode> nodes;

  protected Map<String,String> textures;

  /**
   * The associated renderer for this scene graph. This must be set before attempting to
   * render the scene graph
   */
  protected IScenegraphRenderer renderer;

  /**
   * Booleans for setting if animation has been correctly setup
   */
  private boolean enableAnimation;
  private boolean isAnimationReady;
  private boolean animationError;

  /**
   * Node for holding the transform node used in animation
   */
  private List<TransformNode> animateNodes;

  private final HashMap<String, IAnimator> nameToFunctionMap;

  /**
   * Speed to rotate at in degrees per second
   */
  private final float rotationSpeed = 540 ;


  public Scenegraph()
  {
    root = null;
    meshes = new HashMap<String,util.PolygonMesh<VertexType>>();
    nodes = new HashMap<String,INode>();
    textures = new HashMap<String,String>();

    enableAnimation = true;
    isAnimationReady = false;
    animationError = false;
    animateNodes = new ArrayList<>();

    nameToFunctionMap = new HashMap<>();
    IAnimator propellerAnimation = (transform, time)
            -> transform.rotateZ((float) Math.toRadians((time / 1000f) * rotationSpeed));
    nameToFunctionMap.put("-front_left_arm-propeller-transform", propellerAnimation);
    nameToFunctionMap.put("-front_right_arm-propeller-transform", propellerAnimation);
    nameToFunctionMap.put("-back_left_arm-propeller-transform", propellerAnimation);
    nameToFunctionMap.put("-back_right_arm-propeller-transform", propellerAnimation);
    nameToFunctionMap.put("slow_rotate_light", ((transform, time)
            -> transform.rotateY((float) Math.toRadians((time / 1000f) * rotationSpeed / 4f))));
    nameToFunctionMap.put("lighthouse-lighthouse-light", ((transform, time)
            -> transform.rotateY((float) Math.toRadians((time / 1000f) * rotationSpeed / 8f))));
  }

  public void dispose()
  {
    renderer.dispose();
  }

  /**
   * Sets the renderer, and then adds all the meshes to the renderer.
   * This function must be called when the scene graph is complete, otherwise not all of its
   * meshes will be known to the renderer
   * @param renderer The {@link IScenegraphRenderer} object that will act as its renderer
   * @throws Exception
   */
  @Override
  public void setRenderer(IScenegraphRenderer renderer) throws Exception {
    this.renderer = renderer;

    //now add all the meshes
    for (String meshName:meshes.keySet())
    {
      this.renderer.addMesh(meshName,meshes.get(meshName));
    }

    for (String texName : textures.keySet())
    {
      this.renderer.addTexture(texName,textures.get(texName));
    }

  }


  /**
   * Set the root of the scenegraph, and then pass a reference to this scene graph object
   * to all its node. This will enable any node to call functions of its associated scene graph
   * @param root
   */

  @Override
  public void makeScenegraph(INode root)
  {
    this.root = root;
    this.root.setScenegraph(this);

  }

  /**
   * Draw this scene graph. It delegates this operation to the renderer
   * @param modelView
   */
  @Override
  public void draw(Stack<Matrix4f> modelView) {
    if ((root!=null) && (renderer!=null))
    {
      renderer.draw(root,modelView);
    }
  }

  @Override
  public List<Light> collectLights(Stack<Matrix4f> modelView) {
    if (root != null) {
      return root.getLightsInViewSystem(modelView);
    } else {
      return new ArrayList<>();
    }
  }


  @Override
  public void addPolygonMesh(String name, util.PolygonMesh<VertexType> mesh)
  {
    meshes.put(name,mesh);
  }




  @Override
  public void animate(float time) {
    // Find nodes to be animated.
    if (enableAnimation && !isAnimationReady && !animationError) {
      findAnimationNodes();
    }

    // Do the animation
    if (enableAnimation && isAnimationReady) {
      doAnimation(time);
    }
  }

  /**
   * Add nodes to be animated to animateNodes. Should only be called once.
   */
  private void findAnimationNodes() {
    // Disable animation if there are no nodes to animate
    Set<String> keys = nameToFunctionMap.keySet();
    for (String k : nodes.keySet()) {
      System.out.println(k);
    }
    if (keys.size() == 0) {
      enableAnimation = false;
    }
    for (String animateNodeName : keys) {
      INode animationTransformNode = nodes.get(animateNodeName);
      if (animationTransformNode == null) {
        animationError = true;
        continue;
      }
      if (animationTransformNode instanceof TransformNode) {
        animateNodes.add((TransformNode) animationTransformNode);
        animationTransformNode.setAnimationTransform(new Matrix4f());
        isAnimationReady = true;
      } else {
        animationError = true;
      }
    }
  }

  /**
   * Perform the animation based on the change in time.
   * @param time Time in milliseconds.
   */
  private void doAnimation(float time) {
    for (TransformNode transformNode : animateNodes) {
      nameToFunctionMap.get(transformNode.getName())
              .animate(transformNode.getAnimationTransform(), time);
    }
  }


  @Override
  public void addNode(String name, INode node) {
    nodes.put(name,node);
  }


  @Override
  public INode getRoot() {
    return root;
  }

  @Override
  public Map<String, PolygonMesh<VertexType>> getPolygonMeshes() {
    Map<String,util.PolygonMesh<VertexType>> meshes = new HashMap<String,PolygonMesh<VertexType>>(this.meshes);
    return meshes;
  }

  @Override
  public Map<String, INode> getNodes() {
    Map<String,INode> nodes = new TreeMap<String,INode>();
    nodes.putAll(this.nodes);
    return nodes;
  }

  @Override
  public void addTexture(String name, String path) {
    textures.put(name,path);
  }
}
