# 模型资源目录

本目录用于放置离线识别、说话人分割、声纹提取所需的模型和词表文件。

由于模型文件体积较大，源码仓库不直接托管 `models` 下的二进制资源。请从项目的 GitHub Releases 页面下载模型资源包，并将压缩包内容解压到当前目录。

解压后目录结构应类似：

```text
models
├── paraformer-zh
│   ├── model.int8.onnx
│   └── tokens.txt
├── conformer-zh-stateless2
│   ├── encoder-epoch-99-avg-1.onnx
│   ├── decoder-epoch-99-avg-1.onnx
│   ├── joiner-epoch-99-avg-1.onnx
│   └── tokens.txt
├── vad
│   └── silero_vad.onnx
└── speaker
    ├── pyannote-segmentation-3-0.onnx
    └── 3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx
```

如果程序启动或识别时提示找不到模型文件，请确认模型资源包已经解压到项目根目录下的 `models` 文件夹中。
