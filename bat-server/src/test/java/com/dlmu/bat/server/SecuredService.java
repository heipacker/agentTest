package com.dlmu.bat.server;

/**
 * Created by fupan on 16-4-9.
 */
public class SecuredService extends Service {

    @Override
    public void deleteEverything() {
        if (UserHolder.user.equals("ADMIN")) {
            super.deleteEverything();
        } else {
            throw new IllegalStateException("Not authorized");
        }
    }
}
