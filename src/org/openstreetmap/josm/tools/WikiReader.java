// License: GPL. Copyright 2007 by Immanuel Scholz and others
package org.openstreetmap.josm.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

/**
 * Read a trac-wiki page.
 *
 * @author imi
 */
public class WikiReader {

    private final String baseurl;

    public WikiReader(String baseurl) {
        this.baseurl = baseurl;
    }

    /**
     * Read the page specified by the url and return the content.
     *
     * If the url is within the baseurl path, parse it as an trac wikipage and
     * replace relative pathes etc..
     *
     * @return Either the string of the content of the wiki page.
     * @throws IOException Throws, if the page could not be loaded.
     */
    public String read(String url) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(new URL(url).openStream(), "utf-8"));
        if (url.startsWith(baseurl) && !url.endsWith("?format=txt"))
            return readFromTrac(in, url);
        return readNormal(in);
    }

    private String readNormal(BufferedReader in) throws IOException {
        String b = "";
        for (String line = in.readLine(); line != null; line = in.readLine()) {
            if(!line.contains("[[TranslatedPages]]"))
                b += line.replaceAll(" />", ">") + "\n";
        }
        return "<html>" + b + "</html>";
    }

    private String readFromTrac(BufferedReader in, String url) throws IOException {
        boolean inside = false;
        String b = "";
        for (String line = in.readLine(); line != null; line = in.readLine()) {
            if (line.contains("<div id=\"searchable\">"))
                inside = true;
            else if (line.contains("<div class=\"wikipage searchable\">"))
                inside = true;
            else if (line.contains("<div class=\"buttons\">"))
                inside = false;
            if (inside) {
                b += line.replaceAll("<img src=\"/", "<img src=\""+baseurl+"/")
                         .replaceAll("href=\"/", "href=\""+baseurl+"/")
                         .replaceAll(" />", ">") + "\n";
            }
        }
        return "<html>" + b + "</html>";
    }
}
