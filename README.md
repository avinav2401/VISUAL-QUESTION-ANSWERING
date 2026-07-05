<div align="center">

# 👁️‍🗨️ VisionAssist AI

**See. Understand. Assist.**

</div>

<br>

### 📷 Screenshots

![VisionAssist AI Web UI](screenshot.png)

<br>

Welcome to the **VisionAssist AI** project. This repository represents an exploration into multimodal artificial intelligence, designed to fuse computer vision with natural language processing. The ultimate goal? To empower machines to look at an image, comprehend a question asked about that image, and synthesize an accurate, context-aware answer. 

> [!NOTE]
> **Accessibility First**
> At its core, this project is an accessibility initiative. By giving machines the ability to describe and answer questions about the visual world, we create powerful tools for the visually impaired, allowing them to interact with their surroundings through a seamless interface.
> 
> **Enhanced Voice Features & Auto-Routing:**
> - 🎙️ **Speech-to-Text (Voice Input):** Users can click the microphone button or simply say "Hey Vision" to speak their questions.
> - 🤖 **Intent Auto-Router:** The app features a blazing-fast, low-latency heuristic router. Simply say "Guide me" to automatically start navigation, or "Read this" to instantly extract text. The app selects the correct AI model automatically!
> - 🔊 **Text-to-Speech (Voice Output):** The AI automatically reads the answer out loud. Users can easily toggle this feature on/off.

---

## 🌟 Core Features

This platform is powered by three distinct AI models, seamlessly unified into one interface:

| Mode | Model | Example |
| :--- | :--- | :--- |
| 👁️‍🗨️ **Vision** | `ViLT` | *"What is on the table?"* |
| 📝 **OCR** | `EasyOCR` | *"Read this medicine label."* |
| 🚶 **Navigation** | `YOLOv8` | *"chair ahead"* or *"Path is clear."* |

---

## 🧠 The Philosophy and Purpose

The human brain processes the world through multiple modalities simultaneously. When we see a scene, we don't just register pixel values; we extract semantic meaning. When we hear a question, we map those words to our conceptual understanding of the world. 

This project aims to replicate that dual-processing capability, featuring a production-ready multimodal architecture combining VQA, OCR, and navigation assistance:

| Context | Environment | Focus |
|:---|:---|:---|
| 📱 **The Edge** | Android App | Designed for portability and performance, utilizing a 100% Native Android UI with Java and XML, directly connecting to powerful cloud models. |
| ☁️ **The Cloud** | Web App | Built for unrestricted computational power, leveraging transformer-based vision-language models for multimodal reasoning. |

---

## ⚙️ System Architecture (Vision-and-Language Transformer)

To provide the highest possible accuracy and a unified user experience, both the Android and Web platforms now utilize a single, powerful cloud architecture.

### The ViLT Architecture
*Deployed on Hugging Face Spaces via FastAPI and PyTorch*

The system integrates three powerful pipelines behind a unified API:

- **ViLT (Vision-and-Language Transformer)**: Tokenizes both image patches and text tokens directly into a single transformer for visual QA.
- **YOLOv8 (Ultralytics)**: Real-time object detection for the continuous Navigation Assistant.
- **EasyOCR**: Lightweight Optical Character Recognition for document and sign reading.

```mermaid
graph TD
    subgraph Frontend [Client Interfaces]
        E[Web App <br> WebRTC Camera]
        F[Android App <br> CameraX]
    end

    subgraph Backend [Unified AI Server - FastAPI]
        B[API Router]
        V[ViLT Transformer <br> VQA]
        Y[YOLOv8 <br> Navigation]
        O[EasyOCR <br> Text Reading]
    end

    E -->|API Request| B
    F -->|OkHttp Multipart| B

    B -->|/predict| V
    B -->|/navigate| Y
    B -->|/ocr| O

    V --> D[Synthesized Answer]
    Y --> D
    O --> D
```

---

## 📁 Directory Organization

The repository is modularly structured to separate the training environments from the deployment platforms.

| Directory | Purpose | Contents |
|:---|:---|:---|
| 📓 **`/Model`** | The research and training nexus. | Jupyter notebooks detailing data preprocessing and legacy model generation. |
| 📱 **`/Android app`** | The native mobile application workspace. | A fully native Android application featuring CameraX integration, and a Voice-Guided Intent Auto-Router. |
| 🌐 **`/WebApp`** | The full-stack web portal. | The `backend/` runs the FastAPI inference server with threadpools. The `frontend/` provides a highly responsive web interface utilizing the same Low-Latency Intent Auto-Router for seamless interaction. |

---

## 🚀 Deployment & Setup Guide

### 🌐 Running the Web Platform Locally
Ensure you have **Python 3.10+** installed.

```bash
# 1. Navigate to the backend directory
cd WebApp/backend

# 2. Install machine learning and server dependencies
pip install -r requirements.txt

# 3. Start the FastAPI backend
# Note: The heavy transformer weights download automatically on initial startup.
python main.py
```
> [!TIP]
> Once the server is running on `localhost`, simply open `WebApp/frontend/index.html` in any modern web browser to access the interface!

<br>

### ☁️ Cloud Deployment (Vercel & Hugging Face)

The Web Platform is fully configured for cloud deployment:

1. **Backend (Hugging Face Spaces)**:
   - Create a new Hugging Face Space using the Docker template.
   - Upload the contents of `WebApp/backend`.
   - The FastAPI server will automatically install dependencies and expose the `/predict` endpoint.

2. **Frontend (Vercel)**:
   - Update the `vercel.json` file in `WebApp/frontend` to route `/api/*` to your Hugging Face Space URL.
   - Deploy the repository to [Vercel](https://vercel.com) and set the **Root Directory** to `WebApp/frontend`.

<br>

### 📱 Running the Android Application
Ensure you have the latest version of **Android Studio** installed.

1. Launch Android Studio and select **"Open an existing project"**.
2. Navigate to and select the `Android app/` folder in this repository.
3. Allow the Gradle build system to resolve and sync all dependencies (including `OkHttp`).
4. Connect a physical Android device and click **Run** to compile the fully native APK.

> [!WARNING]
> We strongly recommend a **physical Android device** over an emulator to ensure full camera hardware support for taking pictures of your surroundings.

---

<div align="center">

### 💡 Skills & Concepts Covered

This repository serves as a comprehensive showcase of modern AI and App Development concepts, successfully bridging multiple domains into a single functional product:

| **Domain** | **Concepts Demonstrated** |
| :--- | :--- |
| **Deep Learning & AI** | ✅ ViT & Transformer Architectures<br>✅ Object Detection (YOLOv8)<br>✅ Optical Character Recognition (OCR)<br>✅ Multimodal Learning & Feature Fusion |
| **Voice & NLP** | ✅ Low-Latency Intent Routing<br>✅ Speech Recognition (STT)<br>✅ Text-to-Speech Synthesis (TTS) |
| **Cloud & API Engineering** | ✅ RESTful API Design (FastAPI)<br>✅ Cloud Deployment (Hugging Face Spaces, Vercel)<br>✅ CORS & Reverse Proxying<br>✅ Async Programming & Threadpools |
| **Performance Optimization** | ✅ GPU Hardware Acceleration Support (CUDA)<br>✅ Memory Management (TTLCache)<br>✅ Frontend Blob Management (`createObjectURL`)<br>✅ Disk I/O Minimization (`ImageProxy`) |
| **Software Engineering** | ✅ Native Android App Development (CameraX)<br>✅ Responsive Web UI/UX (Vanilla JS/CSS)<br>✅ Spatial Geometry (Bounding Box Area Sorting)<br>✅ Accessibility-First Design |

</div>

<br>

<div align="center">

### Acknowledgments

This project is built upon the foundational work of the VQA dataset creators, the developers of PyTorch and TensorFlow, and the open-source advancements in transformer architectures by HuggingFace. 

*It stands as a testament to the fact that advanced artificial intelligence can, and should, be utilized to make the world more accessible for everyone.*

</div>