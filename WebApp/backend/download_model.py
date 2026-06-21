from transformers import ViltProcessor, ViltForQuestionAnswering
print('Starting download...')
processor = ViltProcessor.from_pretrained('dandelin/vilt-b32-finetuned-vqa')
model = ViltForQuestionAnswering.from_pretrained('dandelin/vilt-b32-finetuned-vqa')
print('Download complete!')
