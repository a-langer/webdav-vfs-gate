package com.github.alanger.webdav;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.DavResourceFactory;
import org.apache.jackrabbit.webdav.DavResourceLocator;
import org.apache.jackrabbit.webdav.DavServletRequest;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.DavSession;
import org.apache.jackrabbit.webdav.lock.LockManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * See org.apache.jackrabbit.webdav.simple.ResourceFactoryImpl
 */
public class VfsDavResourceFactory implements DavResourceFactory {

    private static Logger log = LoggerFactory.getLogger(VfsDavResourceFactory.class);

    private final LockManager lockMgr;
    private final FileObject root;

    public FileObject getRootObject() {
        return root;
    }

    public VfsDavResourceFactory(LockManager lockMgr, FileObject root) {
        this.lockMgr = lockMgr;
        this.root = root;
    }

    @Override
    public DavResource createResource(DavResourceLocator locator, DavServletRequest request,
            DavServletResponse response) throws DavException {
        DavResource resource;
        try {
            DavSession session = request.getDavSession();
            FileObject fobj = root.resolveFile(locator.getRepositoryPath());

            if (log.isTraceEnabled())
                log.trace("# createResource by request: {}, exist: {}", locator.getRepositoryPath(), fobj.exists());

            resource = createResource(locator, session, request, fobj);
            resource.addLockManager(lockMgr);
        } catch (FileSystemException e) {
            log.debug("createResource by request error ", e);
            throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR, e);
        }
        return resource;
    }

    @Override
    public DavResource createResource(DavResourceLocator locator, DavSession session) throws DavException {
        DavResource resource;
        try {
            FileObject fobj = root.resolveFile(locator.getRepositoryPath());

            if (log.isTraceEnabled())
                log.trace("# createResource by session: {}, exist: {}", locator.getRepositoryPath(), fobj.exists());

            resource = createResource(locator, session, null, fobj);
            resource.addLockManager(lockMgr);
        } catch (FileSystemException e) {
            log.debug("createResource by session error ", e);
            throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR, e);
        }
        return resource;
    }

    protected DavResource createResource(DavResourceLocator locator, DavSession session, DavServletRequest request,
            FileObject fobj) throws DavException, FileSystemException {
        if (!fobj.exists()) {
            boolean isCollection = request != null ? DavMethods.isCreateCollectionRequest(request) : false;
            return new VfsDavResource(locator, this, session, isCollection);
        } else {
            return new VfsDavResource(locator, this, session, fobj);
        }
    }
}
