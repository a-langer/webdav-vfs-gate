package com.github.alanger.webdav;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.util.HttpDateFormat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

/**
 * See org.apache.jackrabbit.server.io.IOUtil
 * <p>
 * 
 * <code>IOUtil</code> provides utility methods used for import and export operations.
 */
public final class IOUtil {

    /**
     * Avoid instantiation
     */
    private IOUtil() {
    }

    /**
     * Constant for undefined modification/creation time
     */
    public static final long UNDEFINED_TIME = DavConstants.UNDEFINED_TIME;

    /**
     * Constant for undefined content length
     */
    public static final long UNDEFINED_LENGTH = -1;

    /**
     * Return the last modification time as formatted string.
     *
     * @return last modification time as string.
     * @see org.apache.jackrabbit.webdav.util.HttpDateFormat#modificationDateFormat()
     */
    public static String getLastModified(long modificationTime) {
        if (modificationTime <= IOUtil.UNDEFINED_TIME) {
            modificationTime = new Date().getTime();
        }
        return HttpDateFormat.modificationDateFormat().format(new Date(modificationTime));
    }

    /**
     * Return the creation time as formatted string.
     *
     * @return creation time as string.
     * @see org.apache.jackrabbit.webdav.util.HttpDateFormat#creationDateFormat()
     */
    public static String getCreated(long createdTime) {
        if (createdTime <= IOUtil.UNDEFINED_TIME) {
            createdTime = 0;
        }
        return HttpDateFormat.creationDateFormat().format(new Date(createdTime));
    }

    /**
     */
    public static void spool(InputStream in, OutputStream out) throws IOException {
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
        } finally {
            in.close();
        }
    }

    /**
     * Build a valid content type string from the given mimeType and encoding:
     * 
     * <pre>
     * &lt;mimeType&gt;; charset="&lt;encoding&gt;"
     * </pre>
     * 
     * If the specified mimeType is <code>null</code>, <code>null</code> is returned.
     *
     * @param mimeType
     * @param encoding
     * @return contentType or <code>null</code> if the specified mimeType is <code>null</code>
     */
    public static String buildContentType(String mimeType, String encoding) {
        String contentType = mimeType;
        if (contentType != null && encoding != null) {
            contentType += "; charset=" + encoding;
        }
        return contentType;
    }

    /**
     * Retrieve the mimeType from the specified contentType.
     *
     * @param contentType
     * @return mimeType or <code>null</code>
     */
    public static String getMimeType(String contentType) {
        String mimeType = contentType;
        if (mimeType == null) {
            // property will be removed.
            // Note however, that jcr:mimetype is a mandatory property with the
            // built-in nt:file nodetype.
            return mimeType;
        }
        // strip any parameters
        int semi = mimeType.indexOf(';');
        return (semi > 0) ? mimeType.substring(0, semi) : mimeType;
    }

    /**
     * Retrieve the encoding from the specified contentType.
     *
     * @param contentType
     * @return encoding or <code>null</code> if the specified contentType is <code>null</code> or does not define a
     *         charset.
     */
    public static String getEncoding(String contentType) {
        // find the charset parameter
        int equal;
        if (contentType == null || (equal = contentType.indexOf("charset=")) == -1) {
            // jcr:encoding property will be removed
            return null;
        }
        String encoding = contentType.substring(equal + 8);
        // get rid of any other parameters that might be specified after the charset
        int semi = encoding.indexOf(';');
        if (semi != -1) {
            encoding = encoding.substring(0, semi);
        }
        return encoding;
    }

    /**
     * Builds a new temp. file from the given input stream.
     * <p>
     * It is left to the user to remove the file as soon as it is not used any more.
     *
     * @param inputStream
     *            the input stream
     * @return temp. file or <code>null</code> if the specified input is <code>null</code>.
     */
    public static File getTempFile(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return null;
        }
        // we need a tmp file, since the import could fail
        File tmpFile = File.createTempFile("__importcontext", ".tmp");
        FileOutputStream out = new FileOutputStream(tmpFile);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        out.close();
        inputStream.close();
        return tmpFile;
    }
}