package mqtt;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class RSmqtt {
    public String eMessage = "";
    public int qos = 2;

    // Передача на сервер: tcp://116.203.78.48:1883, power1000, 1 или 0
    public boolean sendData(String URL,String topic,String content) {
        boolean lRet = true;
        URL = URL.trim(); topic = topic.trim();
        if (!chkParams(URL,topic)) return false;
        MemoryPersistence persistence = new MemoryPersistence();
        try {
            MqttClient sampleClient = new MqttClient(URL, "Fusion_Send", persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            sampleClient.connect(connOpts);
            MqttMessage message = new MqttMessage(content.getBytes());
            message.setQos(qos);
            sampleClient.publish(topic,message);
            sampleClient.disconnect();
        } catch(MqttException e) {
            lRet = errBox(e.getMessage());
        }
        return lRet;
    }

    // Прием данных от сервера: tcp://116.203.78.48:1883, temp - общий топик для всех
    public boolean receiveData(String URL,String topic,final Integer port) {
        boolean lRet = true;
        URL = URL.trim(); topic = topic.trim();
        if (!chkParams(URL,topic)) return false;
        MemoryPersistence persistence = new MemoryPersistence();
        try {
            MqttClient sampleClient = new MqttClient(URL, "Fusion_Receive", persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            sampleClient.setCallback(new MqttCallback() {
                public void connectionLost(Throwable cause) {}
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    byte data[] = ("b'" + message.toString()).getBytes();
                    final DatagramSocket ds = new DatagramSocket();
                    DatagramPacket dp = new DatagramPacket(data,data.length,InetAddress.getByName("127.0.0.1"),port);
                    ds.send(dp);
                }
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });
            sampleClient.connect(connOpts);
            sampleClient.subscribe(topic);
        } catch(Throwable t) {
            lRet = errBox(t.getMessage());
        }
        return lRet;
    }

    public boolean close(String URL) {
        boolean lRet = true;
        URL = URL.trim();
        if (!chkParams(URL,"___")) return false;
        MemoryPersistence persistence = new MemoryPersistence();
        try {
            MqttClient sampleClient = new MqttClient(URL, "Fusion_Receive", persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            sampleClient.connect(connOpts);
            sampleClient.disconnect();
        } catch(MqttException e) {
            lRet = errBox(e.getMessage());
        }
        return lRet;
    }

    private boolean chkParams(String URL, String topic) {
        eMessage = "";
        if (URL.length() == 0)
            return errBox("Сетевой адрес не определен");
        if (!URL.startsWith("tcp://"))
            return errBox("Отсутвует префикс адреса tcp://");
        if (topic.length() == 0)
            return errBox("Значение топика не определено");
        return true;
    }

    private boolean errBox(String eMsg) {
        this.eMessage = "ERROR MQTT: " + eMsg;
        return false;
    }
}
