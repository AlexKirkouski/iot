package mqtt;

import com.google.common.base.Throwables;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class RSmqtt {
    public String eMessage = "";
    public int qos = 2;
    public boolean lRead;
    private String recTopic; // топик для приема, необходимо при восстановлении соединения

    // Передача на сервер: tcp://116.203.78.48:1883, power1000, 1 или 0
    public boolean sendData(String URL,String topic,String content) {
        boolean lRet = true;
        URL = URL.trim(); topic = topic.trim();
        if (!chkParams(URL,topic)) return false;
        MemoryPersistence persistence = new MemoryPersistence();
        try {
            MqttClient mqttClient = new MqttClient(URL, "Fusion_Send", persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            mqttClient.connect(connOpts);
            MqttMessage message = new MqttMessage(content.getBytes());
            message.setQos(qos);
            mqttClient.publish(topic,message);
            mqttClient.disconnect();
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
        recTopic = topic;
        MemoryPersistence persistence = new MemoryPersistence();
        lRead = true;
        try {
            final MqttClient mqttClient = new MqttClient(URL, "Fusion_Receive_" + topic, persistence);
            final MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            connOpts.setAutomaticReconnect(true);
            mqttClient.connect(connOpts);
            mqttClient.subscribe(topic);
            mqttClient.setCallback(new MqttCallbackExtended() {

                // вызывается когда сетевое соединение потеряно
                public void connectionLost(Throwable cause) { }

                // вызывается когда соединение восстановлено после обрыва
                // serverURI запомнен, но topic потерян, поэтому его надо восстановить
                public void connectComplete(boolean reconnect, String serverURI) {
                        try {
                            mqttClient.subscribe(recTopic);
                        } catch (Exception e) {
                        }
                }
                // вызывается когда получено сообщение от топика
                public void messageArrived(String topic, MqttMessage message) {
                    System.out.println(message.toString());
                    try {
                        byte data[] = ("b'" + message.toString()).getBytes();
                        final DatagramSocket ds = new DatagramSocket();
                        DatagramPacket dp = new DatagramPacket(data, data.length, InetAddress.getByName("127.0.0.1"), port);
                        ds.send(dp);
                    } catch (Exception e) {
                        errBox(e.getMessage());
                    }
                }

                // Вызывается, когда доставка сообщения завершена и все подтверждения получены.
                public void deliveryComplete(IMqttDeliveryToken token) {}

            }); // конец CallBack
        } catch (Throwable t) {
            lRet = errBox(t.getMessage());
        }
        return lRet;
    }

    public boolean close(String URL,String topic) {
        boolean lRet = true;
        URL = URL.trim();
        lRead = false;
        if (!chkParams(URL,topic)) return false;
        MemoryPersistence persistence = new MemoryPersistence();
        try {
            MqttClient mqttClient = new MqttClient(URL, "Fusion_Receive_" + topic, persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            mqttClient.connect(connOpts);
            mqttClient.disconnect();
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


