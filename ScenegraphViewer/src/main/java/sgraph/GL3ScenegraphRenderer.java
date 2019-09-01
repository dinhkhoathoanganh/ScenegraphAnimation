package sgraph;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import util.IVertexData;
import util.Material;
import util.TextureImage;
import com.jogamp.opengl.util.texture.Texture;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.*;

/**
 * This is a scene graph renderer implementation that works specifically with the JOGL library
 * It mandates OpenGL 3 and above.
 * @author Amit Shesh
 */
public class GL3ScenegraphRenderer implements IScenegraphRenderer {
    /**
     * The JOGL specific rendering context
     */
    protected GLAutoDrawable glContext;
    /**
     * A table of shader locations and variable names
     */
    protected util.ShaderLocationsVault shaderLocations;
    /**
     * A table of shader variables -> vertex attribute names in each mesh
     */
    protected Map<String,String> shaderVarsToVertexAttribs;

    /**
     * A map to store all the textures
     */
    protected Map<String, TextureImage> textures;
    /**
     * A table of renderers for individual meshes
     */
    protected Map<String,util.ObjectInstance> meshRenderers;

    /**
     * A variable tracking whether shader locations have been set. This must be done before
     * drawing!
     */
    private boolean shaderLocationsSet;

    private Matrix4f proj;
    private int modelviewLocation, projectionLocation, normalmatrixLocation, texturematrixLocation;
    private int materialAmbientLocation, materialDiffuseLocation, materialSpecularLocation, materialShininessLocation;
    private int textureLocation;

    public GL3ScenegraphRenderer()
    {
        meshRenderers = new HashMap<String,util.ObjectInstance>();
        shaderLocations = new util.ShaderLocationsVault();
        shaderLocationsSet = false;

        proj = new Matrix4f();
        proj.identity();
        textures = new HashMap<String,TextureImage>();
    }

    /**
     * Specifically checks if the passed rendering context is the correct JOGL-specific
     * rendering context {@link com.jogamp.opengl.GLAutoDrawable}
     * @param obj the rendering context (should be {@link com.jogamp.opengl.GLAutoDrawable})
     * @throws IllegalArgumentException if given rendering context is not {@link com.jogamp.opengl.GLAutoDrawable}
     */
    @Override
    public void setContext(Object obj) throws IllegalArgumentException
    {
        if (obj instanceof GLAutoDrawable)
        {
            glContext = (GLAutoDrawable)obj;
        }
        else
            throw new IllegalArgumentException("Context not of type GLAutoDrawable");
    }

    /**
     * Add a mesh to be drawn later.
     * The rendering context should be set before calling this function, as this function needs it
     * This function creates a new
     * {@link util.ObjectInstance} object for this mesh
     * @param name the name by which this mesh is referred to by the scene graph
     * @param mesh the {@link util.PolygonMesh} object that represents this mesh
     * @throws Exception
     */
    @Override
    public <K extends IVertexData> void addMesh(String name, util.PolygonMesh<K> mesh) throws Exception
    {
        if (!shaderLocationsSet)
            throw new Exception("Attempting to add mesh before setting shader variables. Call initShaderProgram first");
        if (glContext==null)
            throw new Exception("Attempting to add mesh before setting GL context. Call setContext and pass it a GLAutoDrawable first.");

        if (meshRenderers.containsKey(name))
            return;

        //verify that the mesh has all the vertex attributes as specified in the map
        if (mesh.getVertexCount()<=0)
            return;
        K vertexData = mesh.getVertexAttributes().get(0);
      GL3 gl = glContext.getGL().getGL3();

      for (Map.Entry<String,String> e:shaderVarsToVertexAttribs.entrySet()) {
            if (!vertexData.hasData(e.getValue()))
                throw new IllegalArgumentException("Mesh does not have vertex attribute "+e.getValue());
        }
      util.ObjectInstance obj = new util.ObjectInstance(gl,
              shaderLocations,shaderVarsToVertexAttribs,mesh,name);

      meshRenderers.put(name,obj);
    }

    @Override
    public void addTexture(String name,String path) throws Exception
    {
        TextureImage image = null;
        String imageFormat = path.substring(path.indexOf('.')+1);
        try {
            image = new TextureImage(path,imageFormat,name);
        } catch (IOException e) {
            throw new IllegalArgumentException("Texture "+path+" cannot be read!");
        }


        if (!shaderLocationsSet)
            throw new Exception("Attempting to add texture before setting shader variables; wrong" +
                    "order");
        if (glContext==null)
            throw new Exception("Attempting to add texture before setting GL context; should set " +
                    "context first");

        textures.put(name,image);

        GL3 gl = glContext.getGL().getGL3();
        if (image != null) {
            textures.get(name).getTexture().setTexParameteri(gl, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            textures.get(name).getTexture().setTexParameteri(gl, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            textures.get(name).getTexture().setTexParameteri(gl, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
            textures.get(name).getTexture().setTexParameteri(gl, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
        }
    }

    /**
     * Begin rendering of the scene graph from the root
     * @param root
     * @param modelView
     */
    @Override
    public void draw(INode root, Stack<Matrix4f> modelView)
    {
        drawLights(root, new Matrix4f(modelView.peek()));
        root.draw(this,modelView);
    }

    @Override
    public void dispose()
    {
        for (util.ObjectInstance s:meshRenderers.values())
            s.cleanup(glContext);
    }
    /**
     * Draws a specific mesh.
     * If the mesh has been added to this renderer, it delegates to its correspond mesh renderer
     * This function first passes the material to the shader. Currently it uses the shader variable
     * "vColor" and passes it the ambient part of the material. When lighting is enabled, this method must
     * be overriden to set the ambient, diffuse, specular, shininess etc. values to the shader
     * @param name
     * @param material
     * @param transformation
     */
    @Override
    public void drawMesh(String name, util.Material material,String textureName,final Matrix4f transformation) {
        if (meshRenderers.containsKey(name))
        {
            GL3 gl = glContext.getGL().getGL3();

            FloatBuffer fb16 = Buffers.newDirectFloatBuffer(16);
            FloatBuffer fb = Buffers.newDirectFloatBuffer(4);

            Matrix4f normalmatrix = new Matrix4f(transformation);
            normalmatrix = normalmatrix.invert().transpose();
            if (materialDiffuseLocation< 0) {
                throw new IllegalArgumentException("No shader variable for \" modelviewLocation \"");
            }

            gl.glUniformMatrix4fv(modelviewLocation, 1, false, transformation.get(fb16));
            gl.glUniformMatrix4fv(normalmatrixLocation, 1, false, normalmatrix.get(fb16));

            gl.glUniform3fv(materialAmbientLocation, 1, material.getAmbient().get(fb));
            gl.glUniform3fv(materialDiffuseLocation, 1, material.getDiffuse().get(fb));
            gl.glUniform3fv(materialSpecularLocation, 1, material.getSpecular().get(fb));
            gl.glUniform1f(materialShininessLocation, material.getShininess());

            Matrix4f textureTransform = new Matrix4f();
            if(textures.containsKey(textureName)) {
                TextureImage textureImage = textures.get(textureName);

                Texture texture = textureImage.getTexture();

                if (texture.getMustFlipVertically()) {//for flipping the image vertically
                    textureTransform = new Matrix4f().translate(0,1,0).scale(1,-1,1);
                }

                // This scale changes the texture
                textureTransform.scale(1, 1, 1);
                gl.glUniformMatrix4fv(shaderLocations.getLocation("texturematrix"), 1, false,
                        textureTransform.get(fb16));

                texture.setTexParameteri(gl, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
                texture.bind(gl);
            }
            else {
                // no texture: give white texture
                if(! textures.containsKey("white")) {
                    throw new IllegalArgumentException("have to pass a white texture first");
                }
                TextureImage textureImage = textures.get("white");
                Texture texture = textureImage.getTexture();
                if (!texture.getMustFlipVertically()) {
                    //for flipping the image vertically
                    textureTransform = new Matrix4f().translate(0,1,0).scale(1,-1,1);
                }
                texture.bind(gl);
            }

            gl.glUniformMatrix4fv(texturematrixLocation, 1, false, textureTransform.get(fb16));


            meshRenderers.get(name).draw(glContext);
        }
    }

    @Override
    public void drawLights(INode node, final Matrix4f transformation) {
        GL3 gl = glContext.getGL().getGL3();
        FloatBuffer fb4 = Buffers.newDirectFloatBuffer(4);

        Stack<Matrix4f> lightStack = new Stack<>();
        lightStack.push(new Matrix4f());
        List<util.Light> lights = node.getLightsInViewSystem(lightStack);

        gl.glUniform1i(shaderLocations.getLocation("numLights"),
                lights.size());
        for (int i = 0; i < lights.size(); i++) {
            String name = "light[" + Integer.toString(i) + "].";

            // Position data
            Vector4f pos = lights.get(i).getPosition();
            Matrix4f lightTransformation = new Matrix4f(transformation);
            pos = lightTransformation.transform(pos);
            gl.glUniform4fv(shaderLocations.getLocation(name + "position"), 1, pos.get(fb4));

            // "Material" data
            Material lm = new Material();
            lm.setAmbient(new Vector4f(lights.get(i).getAmbient(), 0));
            lm.setDiffuse(new Vector4f(lights.get(i).getDiffuse(), 0));
            lm.setSpecular(new Vector4f(lights.get(i).getSpecular(), 0));
            sendMaterialData(name, lm, gl);

            // Spotlight data
            sendVec3Data(gl, name + "spotdirection",
                    lights.get(i).getSpotDirection().mul(transformation),
                    Buffers.newDirectFloatBuffer(4));
            gl.glUniform1f(shaderLocations.getLocation(name + "spotangle"),
                    lights.get(i).getSpotCutoff());
        }

    }

    private void sendMaterialData(String shaderVariablePrefix, util.Material material, GL3 gl) {
        FloatBuffer fb = Buffers.newDirectFloatBuffer(4);
        sendVec3Data(gl, shaderVariablePrefix + "ambient",
                material.getAmbient(), fb);
        sendVec3Data(gl, shaderVariablePrefix + "diffuse",
                material.getDiffuse(), fb);
        sendVec3Data(gl, shaderVariablePrefix + "specular",
                material.getSpecular(), fb);
    }

    /**
     * Sends the Vector4f using glUniform3fv.
     */
    private void sendVec3Data(GL3 gl, String shaderVariableName, Vector4f data, FloatBuffer fb) {
        int loc = shaderLocations.getLocation(shaderVariableName);
        if (loc < 0)
            throw new IllegalArgumentException("No shader variable for \" "
                    + shaderVariableName + " \"");

        gl.glUniform3fv(loc, 1, data.get(fb));
    }


    /**
     * Queries the shader program for all variables and locations, and adds them to itself
     * @param shaderProgram
     */
    @Override
    public void initShaderProgram(util.ShaderProgram shaderProgram,Map<String,String> shaderVarsToVertexAttribs)
    {
        Objects.requireNonNull(glContext);
        GL3 gl = glContext.getGL().getGL3();

        shaderLocations = shaderProgram.getAllShaderVariables(gl);
        this.shaderVarsToVertexAttribs = new HashMap<String,String>(shaderVarsToVertexAttribs);
        shaderLocationsSet = true;

        initShaderVariables();

    }

    private void initShaderVariables() {
        //get input variables that need to be given to the shader program
        projectionLocation = shaderLocations.getLocation("projection");
        modelviewLocation = shaderLocations.getLocation("modelview");
        normalmatrixLocation = shaderLocations.getLocation("normalmatrix");
        texturematrixLocation = shaderLocations.getLocation("texturematrix");
        materialAmbientLocation = shaderLocations.getLocation("material.ambient");
        materialDiffuseLocation = shaderLocations.getLocation("material.diffuse");
        materialSpecularLocation = shaderLocations.getLocation("material.specular");
        materialShininessLocation = shaderLocations.getLocation("material.shininess");

        textureLocation = shaderLocations.getLocation("image");
    }


    @Override
    public int getShaderLocation(String name)
    {
        return shaderLocations.getLocation(name);
    }
}