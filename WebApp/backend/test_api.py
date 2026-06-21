import requests

image_url = 'https://upload.wikimedia.org/wikipedia/commons/thumb/f/f4/Leaf_1_web.jpg/800px-Leaf_1_web.jpg'
response = requests.get(image_url)
with open('leaf.jpg', 'wb') as f:
    f.write(response.content)

url = 'http://localhost:8000/predict'
files = {'image': open('leaf.jpg', 'rb')}
data = {'question': 'what color is it?'}

res = requests.post(url, files=files, data=data)
print(res.json())
