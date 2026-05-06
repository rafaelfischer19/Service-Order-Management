<img width="293" height="172" alt="mqtt" src="https://github.com/user-attachments/assets/ea5e42d3-7e01-4e78-a3de-ef1adf96753b" />


# Docker and Eclipse Mosquitto Installation

## 1. Docker Installation

Update the system packages:

```bash
sudo apt-get update

Install required dependencies:

sudo apt-get install ca-certificates curl gnupg lsb-release

Add Docker’s official GPG key:

curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg

Set up the Docker repository:

echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

Update package lists again:

sudo apt-get update

Install Docker Engine:

sudo apt-get install docker-ce docker-ce-cli containerd.io

Add the current user to the Docker group:

sudo usermod -aG docker $USER
2. Eclipse Mosquitto Container Installation

Create Mosquitto directories:

sudo mkdir -p /mosquitto/config
sudo mkdir /mosquitto/data
sudo mkdir /mosquitto/log

Create the Mosquitto configuration file:

sudo nano /mosquitto/config/mosquitto.conf

Add the following configuration:

persistence true
persistence_location /mosquitto/data/
log_dest file /mosquitto/log/mosquitto.log
allow_anonymous true
listener 1883

Save and exit:

CTRL + O  → Save
CTRL + X  → Exit
3. Running the Mosquitto Container

Start the Eclipse Mosquitto container:

docker run -d \
-p 1883:1883 \
-p 9001:9001 \
-v /mosquitto:/mosquitto \
eclipse-mosquitto
MQTT Broker Access

Default MQTT Port:

1883

WebSocket Port:

9001
Technologies Used
Docker
Eclipse Mosquitto
MQTT Protocol
Ubuntu Linux
