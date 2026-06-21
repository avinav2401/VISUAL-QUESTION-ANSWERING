from fastapi import FastAPI, UploadFile, File, Form
from fastapi.middleware.cors import CORSMiddleware
from io import BytesIO
from PIL import Image
import torch
from transformers import ViltProcessor, ViltForQuestionAnswering

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Load the ViLT model and processor
# These will be downloaded from huggingface on first run
processor = ViltProcessor.from_pretrained("dandelin/vilt-b32-finetuned-vqa")
model = ViltForQuestionAnswering.from_pretrained("dandelin/vilt-b32-finetuned-vqa")

@app.post("/predict")
async def predict(question: str = Form(...), image: UploadFile = File(...)):
    image_bytes = await image.read()
    
    # Load and convert image to RGB (ViLT expects PIL images)
    img = Image.open(BytesIO(image_bytes)).convert('RGB')
    
    # Prepare inputs using the ViltProcessor
    encoding = processor(img, question, return_tensors="pt")
    
    # Forward pass
    with torch.no_grad():
        outputs = model(**encoding)
    
    # The model outputs logits for the vocabulary
    logits = outputs.logits[0] # shape (num_labels,)
    
    # Get top 5 predictions using softmax probabilities
    probs = torch.nn.functional.softmax(logits, dim=-1)
    top_5_probs, top_5_indices = torch.topk(probs, 5)
    
    results = []
    for prob, idx in zip(top_5_probs, top_5_indices):
        results.append({
            "answer": model.config.id2label[idx.item()],
            "confidence": prob.item()
        })
        
    return {"predictions": results}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
