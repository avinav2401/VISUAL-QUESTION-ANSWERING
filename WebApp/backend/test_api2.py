import requests

image_url = 'https://images.unsplash.com/photo-1536882240095-0379873feb4e?q=80&w=1000&auto=format&fit=crop'
headers = {'User-Agent': 'Mozilla/5.0'}
response = requests.get(image_url, headers=headers)
with open('leaf2.jpg', 'wb') as f:
    f.write(response.content)

url = 'http://localhost:8000/predict'
files = {'image': open('leaf2.jpg', 'rb')}
data = {'question': 'what color is it?'}

res = requests.post(url, files=files, data=data)
print(res.json())
