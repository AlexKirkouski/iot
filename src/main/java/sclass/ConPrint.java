package sclass;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ConPrint {

    public void print(String cPref,String cMsg) {
        Date date = new Date();
        SimpleDateFormat fDate = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        cMsg = fDate.format(date) + ", " + cPref + ", " + cMsg;
        System.out.println(cMsg);
    }

}
