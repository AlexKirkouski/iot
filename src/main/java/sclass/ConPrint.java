package sclass;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ConPrint {

    public void print(String cPref,String cMsg) {
        Date date = new Date();
        SimpleDateFormat fDate = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        if (cMsg.length() > 0) {
            System.out.println(fDate.format(date) + ", " + cPref + ", " + cMsg);
        } else {
            System.out.println(fDate.format(date) + ", " + cPref);
        }
    }

}
