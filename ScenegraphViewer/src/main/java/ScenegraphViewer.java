import javax.swing.*;

/**
 * Based on file provided by ashesh.
 */
public class ScenegraphViewer
{
  public static void main(String[] args)
  {
    if (args.length < 1) {
        throw new IllegalArgumentException("Requires path to config file");
    }
    String configPath = args[0];

    ArgOptions ao = new ArgOptions();
    ao.parseArgsFromFile(configPath);

    //Schedule a job for the event-dispatching thread:
    //creating and showing this application's GUI.
    javax.swing.SwingUtilities.invokeLater(new Runnable() {
      public void run() {
          createAndShowGUI(ao);
      }
    });
  }

  private static void createAndShowGUI(ArgOptions ao)
  {
    JFrame frame = new JOGLFrame("Scene graph Viewer", ao);
    frame.setVisible(true);
  }
}
