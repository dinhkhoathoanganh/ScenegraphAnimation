import org.joml.Vector3f;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Scanner;

/**
 * Class for modifying options for the program, such as the view camera position and orientation.
 */
public class ArgOptions {
  public String xmlPath;
  public Vector3f lookAtEye, lookAtCenter, droneTranslate, droneRotate;
  public float camRotation;

  public ArgOptions() {
    xmlPath = "";
    lookAtEye = new Vector3f();
    lookAtCenter = new Vector3f();
    camRotation = 0;
    droneTranslate = new Vector3f();
    droneRotate = new Vector3f();
  }

  /**
   * Parses the given file into option variables.
   * @param file Path to file to parse.
   */
  public void parseArgsFromFile(String file) {
    try {
      Scanner inFile = new Scanner(new FileInputStream(file));
      while (inFile.hasNextLine()) {
        String line = inFile.nextLine();

        // Split the line at colon to divide key, value pairs
        String[] pair = line.split("\\s*:\\s*");

        // Handle the key
        switch (pair[0]) {
          case "xml":
            xmlPath = pair[1];
            break;
          case "eye":
            String[] eyeSplit = pair[1].split(" ");
            for (int i = 0; i < 3; i++) {
              lookAtEye.setComponent(i, Float.parseFloat(eyeSplit[i]));
            }
            break;
          case "center":
            String[] eyeCenter = pair[1].split(" ");
            for (int i = 0; i < 3; i++) {
              lookAtCenter.setComponent(i, Float.parseFloat(eyeCenter[i]));
            }
            break;
          case "rotation":
            camRotation = Float.parseFloat(pair[1]);
            break;
          case "drone_translate":
            String[] droneTranslateStr = pair[1].split(" ");
            for (int i = 0; i < 3; i++) {
              droneTranslate.setComponent(i, Float.parseFloat(droneTranslateStr[i]));
            }
            break;
          case "drone_rotate":
            String[] droneRotateStr = pair[1].split(" ");
            for (int i = 0; i < 3; i++) {
              droneRotate.setComponent(i, Float.parseFloat(droneRotateStr[i]));
            }
            break;
        }
      }
      inFile.close();
    } catch (FileNotFoundException e) {
      throw new IllegalArgumentException("Invalid path to config file: " + file);
    }
  }

  public void print() {
    System.out.println("XML: " + xmlPath);
    System.out.println("Eye: " + lookAtEye.toString());
    System.out.println("Center: " + lookAtCenter.toString());
    System.out.println("CamRot: " + camRotation);
  }
}