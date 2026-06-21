import requests
import time

url = 'http://localhost:8000/predict'
files = {'image': open('leaf2.jpg', 'rb')}
data = {'question': 'WHAT IS COLOUR?'}

for i in range(30):
    try:
        res = requests.post(url, files=files, data=data)
        print("SUCCESS:", res.json())
        break
    except Exception as e:
        print(f"Waiting for server... Attempt {i+1}/30")
        time.sleep(10)
