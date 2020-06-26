package com.github.alanger.webdav;

import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavSessionProvider;
import org.apache.jackrabbit.webdav.WebdavRequest;

public class VfsDavSessionProvider implements DavSessionProvider {

    @Override
    public boolean attachSession(WebdavRequest request) throws DavException {
        request.setDavSession(new VfsDavSession());
        return true;
    }

    @Override
    public void releaseSession(WebdavRequest request) {
        request.setDavSession(null);
    }
}
