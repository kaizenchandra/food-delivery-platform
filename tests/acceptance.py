"""OIDC browser acceptance includes concurrent checkout verification."""
import pathlib, subprocess
subprocess.run(['node',str(pathlib.Path(__file__).with_name('oidc-workflows.cjs'))],check=True)
