"""Optional GPU syntax regression check: pip install moderngl, then run this script.

Uses EGL, suitable for a headless Mesa environment. Veil's camera directive is
represented by a minimal uniform block; this is not a full Veil client test.
"""
from pathlib import Path
import moderngl

source = (Path(__file__).resolve().parents[1] /
          'src/main/resources/assets/veil/pinwheel/shaders/program/light/indirect_sphere.comp').read_text()
source = source.replace('#veil:buffer veil:camera VeilCamera',
                        'layout(std140, binding=1) uniform Camera { vec3 CameraPosition; } VeilCamera;')
context = moderngl.create_standalone_context(backend='egl', require=430)
shader = context.compute_shader(source)
print('Compiled Veil indirect-sphere compute shader:', context.info['GL_RENDERER'])
shader.release()
context.release()
