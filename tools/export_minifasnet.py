"""
Usage:  python tools/export_minifasnet.py [output_dir]      (default: models)
Needs:  torch, onnx, onnxruntime, numpy
"""
import hashlib
import importlib.util
import os
import sys
import tempfile
import urllib.request
from collections import OrderedDict

import numpy as np
import onnx
import onnxruntime as ort
import torch

UPSTREAM = "https://raw.githubusercontent.com/minivision-ai/Silent-Face-Anti-Spoofing"
COMMIT = "b6d5f04ad78778917853b25c778acef6d5626d15"
MODEL_SOURCE = "src/model_lib/MiniFASNet.py"
WEIGHTS = {
    # weight file: (class name, sha256 of the .pth, ONNX file name)
    "2.7_80x80_MiniFASNetV2.pth": (
        "MiniFASNetV2", "a5eb02e1843f19b5386b953cc4c9f011c3f985d0ee2bb9819eea9a142099bec0",
        "minifasnet_v2_2.7_80x80.onnx"),
    "4_0_0_80x80_MiniFASNetV1SE.pth": (
        "MiniFASNetV1SE", "84ee1d37d96894d5e82de5a57df044ef80a58be2b218b5ed7cdfd875ec2f5990",
        "minifasnet_v1se_4.0_80x80.onnx"),
}
MAX_LOGIT_DIFF = 1e-4


def download(path, target):
    url = "%s/%s/%s" % (UPSTREAM, COMMIT, path)
    urllib.request.urlretrieve(url, target)
    return target


def sha256(path):
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else "models"
    os.makedirs(out_dir, exist_ok=True)
    work = tempfile.mkdtemp(prefix="minifasnet-")

    spec = importlib.util.spec_from_file_location(
        "MiniFASNet", download(MODEL_SOURCE, os.path.join(work, "MiniFASNet.py")))
    model_lib = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(model_lib)

    rng = np.random.default_rng(0)
    for weight_file, (class_name, expected_sha, onnx_name) in WEIGHTS.items():
        weights = download("resources/anti_spoof_models/" + weight_file, os.path.join(work, weight_file))
        if sha256(weights) != expected_sha:
            sys.exit("weight hash mismatch for %s" % weight_file)

        state = torch.load(weights, map_location="cpu", weights_only=True)
        state = OrderedDict((k[7:] if k.startswith("module.") else k, v) for k, v in state.items())
        model = getattr(model_lib, class_name)(conv6_kernel=(5, 5))
        model.load_state_dict(state, strict=True)
        model.eval()

        target = os.path.join(out_dir, onnx_name)
        torch.onnx.export(model, torch.zeros(1, 3, 80, 80), target, opset_version=13, input_names=["input"],
                          output_names=["output"], do_constant_folding=True, dynamo=False)
        onnx.checker.check_model(onnx.load(target))

        session = ort.InferenceSession(target, providers=["CPUExecutionProvider"])
        worst = 0.0
        for _ in range(16):
            x = (rng.random((1, 3, 80, 80)) * 255).astype(np.float32)
            with torch.no_grad():
                expected = model(torch.from_numpy(x)).numpy()
            actual = session.run(None, {"input": x})[0]
            worst = max(worst, float(np.abs(expected - actual).max()))
        if worst > MAX_LOGIT_DIFF:
            sys.exit("%s: ONNX differs from PyTorch by %g" % (onnx_name, worst))
        print("%s  sha256 %s  max |logit diff| %.2e" % (onnx_name, sha256(target), worst))


if __name__ == "__main__":
    main()
