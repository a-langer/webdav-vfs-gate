package com.github.alanger.webdav;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import javax.servlet.ServletException;

import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.mock.web.MockServletContext;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class VfsWebDavTest {

    static MockServletConfig config;
    static MockServletContext context;
    static VfsWebDavServlet servlet;

    protected static int deleteFile(String path) throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("DELETE");
        request.setRequestURI(path);
        servlet.service(request, response);
        return response.getStatus();
    }

    @BeforeClass
    public static void before() throws Throwable {
        config = new MockServletConfig();
        context = (MockServletContext) config.getServletContext();
        config.addInitParameter("rootpath", System.getProperty("rootpath"));

        servlet = new VfsWebDavServlet();
        servlet.init(config);

        // Clean test files
        deleteFile("/test1/new_file.txt");
        deleteFile("/test1/new_file_copy.txt");
        deleteFile("/test1/new_file_move.txt");
        deleteFile("/test1/new_dir");
        deleteFile("/test1/new_dir_copy");
        deleteFile("/test1/new_dir_move");
    }

    @Test
    public void test01_getFile1Test() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("GET");
        request.setRequestURI("/test1/file1.txt");
        servlet.service(request, response);
        assertEquals(200, response.getStatus());
        assertEquals(5, response.getContentLength());
        assertEquals("text1", response.getContentAsString());
    }

    @Test
    public void test02_getFile2Test() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("GET");
        request.setRequestURI("/test1/file2.txt");
        servlet.service(request, response);
        assertEquals(200, response.getStatus());
        assertEquals(5, response.getContentLength());
        assertEquals("text2", response.getContentAsString());
    }

    @Test
    public void test03_getFileNotFoundTest() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("GET");
        request.setRequestURI("/test1/_not_found_file_.txt");
        servlet.service(request, response);
        assertEquals(404, response.getStatus()); // 404 Not Found
    }

    @Test
    public void test04_headFileTest() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("HEAD");
        request.setRequestURI("/test1/file1.txt");
        servlet.service(request, response);
        assertEquals(200, response.getStatus());
        assertEquals(5, response.getContentLength());
    }

    @Test
    public void test05_headFileNotFoundTest() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.setMethod("HEAD");
        request.setRequestURI("/test1/_not_found_file_.txt");
        servlet.service(request, response);
        assertEquals(404, response.getStatus()); // 404 Not Found
    }

    @Test
    public void test06_propfindFileTest() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("PROPFIND");
        request.setRequestURI("/test1/file1.txt");
        servlet.service(request, response);
        assertEquals(207, response.getStatus()); // 207 Multi-Status
        assertEquals("text/xml; charset=utf-8".toLowerCase(), response.getContentType().toLowerCase());
    }

    @Test
    public void test07_proppatchEmptyContentFile1Test() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("PROPPATCH");
        request.setRequestURI("/test1/file1.txt");
        servlet.service(request, response);
        assertEquals(400, response.getStatus()); // 400 Bad Request
    }

    @Test
    public void test08_proppatchFileTest() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("PROPPATCH");
        request.setRequestURI("/test1/file1.txt");
        request.setContent(("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n"
                + "<propertyupdate xmlns=\"DAV:\" xmlns:u=\"mynamespace\">\n" + "  <set><prop>\n"
                + "    <u:myprop>myvalue</u:myprop>\n" + "  </prop></set>\n" + "</propertyupdate>").getBytes());
        servlet.service(request, response);
        assertEquals(207, response.getStatus()); // 207 Multi-Status
    }

    @Test
    public void test09_putEmptyContentFileTest() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("PUT");
        request.setRequestURI("/test1/new_file.txt");
        servlet.service(request, response);
        assertEquals(201, response.getStatus()); // 201 Created
        assertEquals(0, response.getContentLength());
    }

    @Test
    public void test10_putFileTest() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("PUT");
        request.setRequestURI("/test1/new_file.txt");
        request.setContent("new_text".getBytes());
        servlet.service(request, response);
        assertEquals(204, response.getStatus()); // 204 No Content

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("GET");
        request.setRequestURI("/test1/new_file.txt");
        servlet.service(request, response);
        assertEquals(200, response.getStatus());
        assertEquals("new_text", response.getContentAsString());
        assertEquals(8, response.getContentLength());

        assertEquals(204, deleteFile("/test1/new_file.txt"));
    }

    @Test
    public void test11_mkcolDirTest() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("MKCOL");
        request.setRequestURI("/test1/new_dir");
        servlet.service(request, response);
        assertEquals(201, response.getStatus());
        assertEquals(0, response.getContentLength());

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("MKCOL");
        request.setRequestURI("/test1/new_dir/new_sub_dir");
        servlet.service(request, response);
        assertEquals(201, response.getStatus());
        assertEquals(0, response.getContentLength());
    }

    @Test
    public void test12_copyFileTest() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("COPY");
        request.setRequestURI("/test1/file1.txt");
        request.addHeader("Destination", "/test1/new_dir/file1.txt");
        servlet.service(request, response);
        assertEquals(201, response.getStatus());

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("COPY");
        request.setRequestURI("/test1/file2.txt");
        request.addHeader("Destination", "/test1/new_dir/file2.txt");
        servlet.service(request, response);
        assertEquals(201, response.getStatus());

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("COPY");
        request.setRequestURI("/test1/file3.txt");
        request.addHeader("Destination", "/test1/new_dir/file3.txt");
        servlet.service(request, response);
        assertEquals(201, response.getStatus());

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("COPY");
        request.setRequestURI("/test1/file1.txt");
        request.addHeader("Destination", "/test1/new_dir/new_sub_dir/file1.txt");
        servlet.service(request, response);
        assertEquals(201, response.getStatus());

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("COPY");
        request.setRequestURI("/test1/file2.txt");
        request.addHeader("Destination", "/test1/new_dir/new_sub_dir/file2.txt");
        servlet.service(request, response);
        assertEquals(201, response.getStatus());

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("COPY");
        request.setRequestURI("/test1/file3.txt");
        request.addHeader("Destination", "/test1/new_dir/new_sub_dir/file3.txt");
        servlet.service(request, response);
        assertEquals(201, response.getStatus());
    }

    @Test
    public void test13_copyDirTest() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("COPY");
        request.setRequestURI("/test1/new_dir/new_sub_dir");
        request.addHeader("Destination", "/test1/new_dir/new_sub_dir_copy");
        servlet.service(request, response);
        assertEquals(201, response.getStatus());
    }

    @Test
    public void test14_moveDirTest() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("MOVE");
        request.setRequestURI("/test1/new_dir/new_sub_dir_copy");
        request.addHeader("Destination", "/test1/new_dir/new_sub_dir_move");
        servlet.service(request, response);
        assertEquals(201, response.getStatus());
    }

    @Test
    public void test15_getNewFileTest() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("GET");
        request.setRequestURI("/test1/new_dir/file1.txt");
        servlet.service(request, response);
        assertEquals(200, response.getStatus());
        assertEquals(5, response.getContentLength());
        assertEquals("text1", response.getContentAsString());

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("GET");
        request.setRequestURI("/test1/new_dir/file2.txt");
        servlet.service(request, response);
        assertEquals(200, response.getStatus());
        assertEquals(5, response.getContentLength());
        assertEquals("text2", response.getContentAsString());

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("GET");
        request.setRequestURI("/test1/new_dir/file3.txt");
        servlet.service(request, response);
        assertEquals(200, response.getStatus());
        assertEquals(5, response.getContentLength());
        assertEquals("text3", response.getContentAsString());
    }

    @Test
    public void test16_getNewSubFileTest() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("GET");
        request.setRequestURI("/test1/new_dir/new_sub_dir/file1.txt");
        servlet.service(request, response);
        assertEquals(200, response.getStatus());
        assertEquals(5, response.getContentLength());
        assertEquals("text1", response.getContentAsString());

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("GET");
        request.setRequestURI("/test1/new_dir/new_sub_dir/file2.txt");
        servlet.service(request, response);
        assertEquals(200, response.getStatus());
        assertEquals(5, response.getContentLength());
        assertEquals("text2", response.getContentAsString());

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("GET");
        request.setRequestURI("/test1/new_dir/new_sub_dir/file3.txt");
        servlet.service(request, response);
        assertEquals(200, response.getStatus());
        assertEquals(5, response.getContentLength());
        assertEquals("text3", response.getContentAsString());
    }

    @Test
    public void test17_getNewSubMoveFileTest() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("GET");
        request.setRequestURI("/test1/new_dir/new_sub_dir_move/file1.txt");
        servlet.service(request, response);
        assertEquals(200, response.getStatus());
        assertEquals(5, response.getContentLength());
        assertEquals("text1", response.getContentAsString());

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("GET");
        request.setRequestURI("/test1/new_dir/new_sub_dir_move/file2.txt");
        servlet.service(request, response);
        assertEquals(200, response.getStatus());
        assertEquals(5, response.getContentLength());
        assertEquals("text2", response.getContentAsString());

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("GET");
        request.setRequestURI("/test1/new_dir/new_sub_dir_move/file3.txt");
        servlet.service(request, response);
        assertEquals(200, response.getStatus());
        assertEquals(5, response.getContentLength());
        assertEquals("text3", response.getContentAsString());
    }

    @Test
    public void test18_deleteFileTest() throws Throwable {
        assertEquals(204, deleteFile("/test1/new_dir/file1.txt"));
        assertEquals(404, deleteFile("/test1/new_dir/file1.txt"));
        assertEquals(204, deleteFile("/test1/new_dir/file2.txt"));
        assertEquals(404, deleteFile("/test1/new_dir/file2.txt"));
        assertEquals(204, deleteFile("/test1/new_dir/file3.txt"));
        assertEquals(404, deleteFile("/test1/new_dir/file3.txt"));
    }

    @Test
    public void test19_deleteDirTest() throws Throwable {
        assertEquals(204, deleteFile("/test1/new_dir"));
        assertEquals(404, deleteFile("/test1/new_dir"));
    }

    @Test(expected = Test.None.class)
    public void test20_fileSysyemBuilderTest() throws Throwable {
        MockServletConfig config = new MockServletConfig();
        config.addInitParameter("rootpath", System.getProperty("rootpath"));
        config.addInitParameter("builder", "org.apache.commons.vfs2.provider.ftps.FtpsFileSystemConfigBuilder");
        config.addInitParameter("builder.controlEncoding", "UTF-8");
        config.addInitParameter("builder.connectTimeout", "5000");
        config.addInitParameter("builder.userDirIsRoot", "false");
        config.addInitParameter("builder.autodetectUtf8", "true");
        VfsWebDavServlet servlet = new VfsWebDavServlet();
        servlet.init(config);
    }

    @Test(expected = ServletException.class)
    public void test21_fileSysyemBuilderMethodNotFoundExceptionTest() throws Throwable {
        MockServletConfig config = new MockServletConfig();
        config.addInitParameter("rootpath", System.getProperty("rootpath"));
        config.addInitParameter("builder", "org.apache.commons.vfs2.provider.ftps.FtpsFileSystemConfigBuilder");
        config.addInitParameter("builder.notFoundMethod", "value");
        VfsWebDavServlet servlet = new VfsWebDavServlet();
        servlet.init(config);
    }

    @Test(expected = ServletException.class)
    public void test22_fileSysyemBuilderClassNotFoundExceptionTest() throws Throwable {
        MockServletConfig config = new MockServletConfig();
        config.addInitParameter("rootpath", System.getProperty("rootpath"));
        config.addInitParameter("builder", "not.found.FileSystemConfigBuilder");
        VfsWebDavServlet servlet = new VfsWebDavServlet();
        servlet.init(config);
    }

    @Test(expected = Test.None.class)
    public void test23_cacheStrategyTest() throws Throwable {
        MockServletConfig config = new MockServletConfig();
        config.addInitParameter("rootpath", System.getProperty("rootpath"));
        config.addInitParameter("cache-strategy", "oncall");
        VfsWebDavServlet servlet = new VfsWebDavServlet();
        servlet.init(config);
    }

    @Test(expected = ServletException.class)
    public void test24_cacheStrategyExceptionTest() throws Throwable {
        MockServletConfig config = new MockServletConfig();
        config.addInitParameter("rootpath", System.getProperty("rootpath"));
        config.addInitParameter("cache-strategy", "notValidStrategy");
        VfsWebDavServlet servlet = new VfsWebDavServlet();
        servlet.init(config);
    }

    @Test(expected = Test.None.class)
    public void test25_filesCacheTest() throws Throwable {
        MockServletConfig config = new MockServletConfig();
        config.addInitParameter("rootpath", System.getProperty("rootpath"));
        config.addInitParameter("files-cache", "org.apache.commons.vfs2.cache.SoftRefFilesCache");
        VfsWebDavServlet servlet = new VfsWebDavServlet();
        servlet.init(config);
    }

    @Test(expected = ServletException.class)
    public void test26_filesCacheExceptionTest() throws Throwable {
        MockServletConfig config = new MockServletConfig();
        config.addInitParameter("rootpath", System.getProperty("rootpath"));
        config.addInitParameter("files-cache", "notValidFilesCache");
        VfsWebDavServlet servlet = new VfsWebDavServlet();
        servlet.init(config);
    }

    @Test
    public void test27_loggerNameTest() throws Throwable {
        MockServletConfig config = new MockServletConfig();
        config.addInitParameter("rootpath", System.getProperty("rootpath"));
        config.addInitParameter("logger-name", "com.github.alanger.webdav.CustomLoggerName");
        VfsWebDavServlet servlet = new VfsWebDavServlet();
        servlet.init(config);
        assertEquals("com.github.alanger.webdav.CustomLoggerName", servlet.getLogger().getName());
    }

    @Test
    public void test28_auditMethodsTest() throws Throwable {
        MockServletConfig config = new MockServletConfig();
        config.addInitParameter("rootpath", System.getProperty("rootpath"));
        config.addInitParameter("audit-methods", "GET,MKCOL,DELETE,COPY,MOVE,PUT,PROPPATH");
        VfsWebDavServlet servlet = new VfsWebDavServlet();
        servlet.init(config);
        assertTrue(servlet.getAuditMethods().contains("GET"));
        assertTrue(servlet.getAuditMethods().contains("MKCOL"));
        assertTrue(servlet.getAuditMethods().contains("DELETE"));
        assertTrue(servlet.getAuditMethods().contains("COPY"));
        assertTrue(servlet.getAuditMethods().contains("MOVE"));
        assertTrue(servlet.getAuditMethods().contains("PUT"));
        assertTrue(servlet.getAuditMethods().contains("PROPPATH"));
        assertFalse(servlet.getAuditMethods().contains("PROPFING"));
        assertFalse(servlet.getAuditMethods().contains("HEAD"));
        assertFalse(servlet.getAuditMethods().contains("POST"));
    }
}
