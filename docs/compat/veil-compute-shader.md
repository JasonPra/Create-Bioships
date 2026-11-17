# Veil 4.3.2 compute shader override

The resource at `assets/veil/pinwheel/shaders/program/light/indirect_sphere.comp`
overrides Veil's bundled shader. Its only functional change is an explicit
`#version 430 core`, required by the compute shader and its storage-buffer
qualifiers. Without it, Intel's shader compiler reports `readonly` as a reserved
word and rejects the shader.

The original shader comes from FoundryMC/Veil under LGPL-3.0; the license texts
are retained here. Recheck whether this override is needed when updating Veil.

Optional headless syntax check: install `moderngl` and run
`python tools/verify_veil_compute_shader.py`. This substitutes a minimal camera
uniform block for Veil's custom directive and compiles through EGL. It does not
replace a client check on the affected Intel driver.
