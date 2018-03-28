package cc.waken;

import cc.waken.config.IStep;
import cc.waken.config.TaskStep;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Hello world!
 * Author: Zuo, Mang
 * Email: zuomangjk@gmail.com
 */
public class App 
{
    private static void findAndAddClassesInPackageByFile(String packageName, String filePath, final boolean recursive, Set<Class<?>> classes) {
        File dir = new File(filePath);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] dirFiles = dir.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                return (recursive && pathname.isDirectory()) || (pathname.getName().endsWith(".class"));
            }
        });

        for (File file : dirFiles) {
            if (file.isDirectory()) {
                findAndAddClassesInPackageByFile(packageName + "." +  file.getName(), file.getAbsolutePath(), recursive, classes );
            } else {
                String className = file.getName().substring(0, file.getName().length() - 6);
                try {
                    classes.add(Thread.currentThread().getContextClassLoader().loadClass(packageName + "." + className));
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void findAndAddClassInPackageByJar(String packageName, URL jarUrl, final boolean recursive, Set<Class<?>> classes) {
        String packageDir = packageName.replace('.', '/');
        JarFile jar;
        try {
            jar = ((JarURLConnection) jarUrl.openConnection()).getJarFile();
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.charAt(0) == '/') {
                    name = name.substring(1);
                }
                if (name.startsWith(packageDir)) {
                    int idx = name.lastIndexOf('/');

                    // 说明不是一个包，将 / 替换成 点，构建 package name
                    if (idx != -1) {
                        packageName = name.substring(0, idx).replace('/', '.');
                    }
                    if ((idx != -1) || recursive) {
                        if (name.endsWith(".class") && !entry.isDirectory()) {
                            String className = name.substring(packageName.length() + 1, name.length() - 6);
                            try {
                                classes.add(Class.forName(packageName + "." + className));
                            } catch (ClassNotFoundException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void AddStepFromClassAnnotation(Map<Integer, List<IStep>> iSteps, Set<Class<?>> classes) {
        for (Class<?> c : classes) {
            TaskStep taskStep = c.getAnnotation(TaskStep.class);
            boolean active = taskStep.active();
            int order = taskStep.step();

            if (active) {
                if (iSteps.get(order) == null) {
                    iSteps.put(order, new LinkedList<IStep>());
                }
                try {
                    iSteps.get(order).add((IStep) c.newInstance());
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main( String[] args )
    {
        String packageName = "cc.waken.steps";
        String packageDir = packageName.replace('.', '/');
        Set<Class<?>> classes = new LinkedHashSet<Class<?>>();
        boolean recursive = true;
        Enumeration<URL> urls;
        try {
            urls = Thread.currentThread().getContextClassLoader().getResources(packageDir);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                System.out.println("Package url: " + url.toString());
                String protocol = url.getProtocol();
                System.out.println(protocol);
                if ("file".equals(protocol)) {
                    System.out.println("scan file package");
                    String filePath = URLDecoder.decode(url.getFile(), "UTF-8");
                    findAndAddClassesInPackageByFile(packageName, filePath, recursive, classes);
                } else if ("jar".equals(protocol)) {
                    System.out.println("scan jar package");
                    findAndAddClassInPackageByJar(packageName, url, recursive, classes);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println(classes);

        // Add Step Object to List
        Map<Integer, List<IStep>> iSteps = new TreeMap<Integer, List<IStep>>();
        AddStepFromClassAnnotation(iSteps, classes);

        // Run all steps
        for (int order : iSteps.keySet()) {
            List<IStep> steps = iSteps.get(order);
            for (IStep step : steps) {
                step.run();
            }
        }

        SortedSet<Class<?>> stepClasses = new TreeSet<Class<?>>(new Comparator<Class<?>>() {
            public int compare(Class<?> o1, Class<?> o2) {
                return o1.getAnnotation(TaskStep.class).step() - o2.getAnnotation(TaskStep.class).step();
            }
        });

        for ( Class<?> c : classes ) {
            if ( c.getAnnotation(TaskStep.class).active() ) {
                stepClasses.add(c);
            }
        }

        System.out.println(stepClasses);
    }
}
