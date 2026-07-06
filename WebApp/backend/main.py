import os

from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from io import BytesIO
from PIL import Image
import torch
from transformers import ViltProcessor, ViltForQuestionAnswering
import numpy as np
import easyocr
from ultralytics import YOLO
from cachetools import TTLCache
import re

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Setup Device for GPU Acceleration
device = "cuda" if torch.cuda.is_available() else "cpu"
print(f"Using device: {device}")

# Load the ViLT model and processor
processor = ViltProcessor.from_pretrained("dandelin/vilt-b32-finetuned-vqa")
model = ViltForQuestionAnswering.from_pretrained("dandelin/vilt-b32-finetuned-vqa").to(device)

# Initialize EasyOCR (uses GPU if available automatically, but we can force it)
ocr_reader = easyocr.Reader(['en'], gpu=(device == "cuda"))

# Initialize YOLOv8 for Navigation (auto-detects device)
yolo_model = YOLO("yolov8n.pt")

# Session store with TTL (expires after 1 hour, max 1000 active users)
session_memory = TTLCache(maxsize=1000, ttl=3600)


def resolve_context(question: str, last_answer: str) -> str:
    if not last_answer:
        return question
    
    q_lower = question.lower()
    if re.search(r'\b(it|this|that|there)\b', q_lower):
        # Replace simple pronouns with the last answer subject
        resolved = re.sub(r'\b(it|this|that|there)\b', last_answer, question, flags=re.IGNORECASE)
        return resolved
    return question

@app.post("/predict")
def predict(question: str = Form(...), session_id: str = Form(None), image: UploadFile = File(...)):
    image_bytes = image.file.read()
    try:
        img = Image.open(BytesIO(image_bytes))
        from PIL import ImageOps
        img = ImageOps.exif_transpose(img).convert('RGB')
    except Exception as e:
        raise HTTPException(status_code=400, detail="Invalid image format")
    
    context_question = question
    if session_id and session_id in session_memory:
        history = session_memory[session_id]
        context_question = resolve_context(question, history)
        
    encoding = processor(img, context_question, return_tensors="pt").to(device)
    
    with torch.no_grad():
        outputs = model(**encoding)
    
    logits = outputs.logits[0]
    probs = torch.nn.functional.softmax(logits, dim=-1)
    top_5_probs, top_5_indices = torch.topk(probs, 5)
    
    results = []
    for prob, idx in zip(top_5_probs, top_5_indices):
        results.append({
            "answer": model.config.id2label[idx.item()],
            "confidence": prob.item()
        })
        
    best_answer = results[0]["answer"]
    
    if session_id:
        session_memory[session_id] = best_answer
        
    return {"predictions": results, "context_used": context_question}

@app.post("/ocr")
def extract_text(image: UploadFile = File(...)):
    image_bytes = image.file.read()
    try:
        img = Image.open(BytesIO(image_bytes))
        from PIL import ImageOps
        img = ImageOps.exif_transpose(img).convert('RGB')
        import numpy as np
        img_np = np.array(img)
        
        # Try different rotations to handle sideways text
        best_results = []
        best_score = -1
        
        for angle in [0, 90, 180, 270]:
            if angle != 0:
                rotated_img = img.rotate(angle, expand=True)
                current_np = np.array(rotated_img)
            else:
                current_np = img_np
                
            results = ocr_reader.readtext(current_np)
            
            # Calculate a score based on text length and confidence
            score = 0
            for res in results:
                text = res[1]
                prob = res[2]
                if prob > 0.3:
                    score += len(text) * prob
                    
            if score > best_score:
                best_score = score
                best_results = results
                
            # If we found a very good score on the first try, we can stop early
            if angle == 0 and score > 50:
                break
                
        results = best_results
    except Exception as e:
        raise HTTPException(status_code=400, detail="Failed to process image for OCR")
    
    text_lines = [res[1] for res in results]
    full_text = " ".join(text_lines)
    
    if not full_text.strip():
        full_text = "No text could be read from the image."
        
    return {"text": full_text}

@app.post("/navigate")
def navigate(image: UploadFile = File(...)):
    image_bytes = image.file.read()
    try:
        img = Image.open(BytesIO(image_bytes))
        from PIL import ImageOps
        img = ImageOps.exif_transpose(img).convert('RGB')
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid image format")
    
    results = yolo_model(img)
    
    detections = []
    for r in results:
        boxes = r.boxes
        for box in boxes:
            cls_id = int(box.cls[0])
            cls_name = yolo_model.names[cls_id]
            conf = float(box.conf[0])
            xyxy = box.xyxy[0].tolist()
            
            # Lowered threshold to 0.25 for better safety in navigation
            if conf > 0.25:
                area = (xyxy[2] - xyxy[0]) * (xyxy[3] - xyxy[1])
                detections.append({
                    "object": cls_name,
                    "confidence": conf,
                    "box": xyxy,
                    "area": area
                })
                
    # Sort detections by area descending (largest object is closest to the camera)
    detections.sort(key=lambda x: x["area"], reverse=True)
    
    return {"detections": detections}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
