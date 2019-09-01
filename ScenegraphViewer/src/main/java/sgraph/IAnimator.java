package sgraph;

import org.joml.Matrix4f;

public interface IAnimator {
  void animate(Matrix4f transform, float time);
}
