package com.redhat.sso.ninja.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;

public abstract class IOUtils2 {

    public static String toStringAndClose(InputStream is) throws IOException {
        try {
            return IOUtils.toString(is, "UTF-8");
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    public static void writeAndClose(byte[] bytes, OutputStream out) throws IOException {
        try {
            out.write(bytes);
            out.flush();
        } finally {
            IOUtils.closeQuietly(out);
        }
    }

    public static abstract class DelegateMethod {
        public abstract Object process(InputStream is);
    }

    public static Object autoCloseFileInputStream(File file, DelegateMethod delegateMethod) throws FileNotFoundException {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            return delegateMethod.process(fis);
        } finally {
            if (fis != null)
                IOUtils.closeQuietly(fis);
        }
    }
}

