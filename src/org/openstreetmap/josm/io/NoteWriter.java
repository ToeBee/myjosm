// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.io;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Locale;

import org.openstreetmap.josm.data.notes.Note;
import org.openstreetmap.josm.data.notes.NoteComment;
import org.openstreetmap.josm.data.osm.NoteData;
import org.openstreetmap.josm.data.osm.User;

public class NoteWriter extends XmlWriter {

    private final SimpleDateFormat ISO8601_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.ENGLISH);

    public NoteWriter(PrintWriter out) {
        super(out);
    }

    public NoteWriter(OutputStream out) {
        super(new PrintWriter(new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))));
    }

    public void write(NoteData data) {
        out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        out.println("<osm-notes>");
        for (Note note : data.getNotes()) {
            out.print("  <note ");
            out.print("id=\"" + note.getId() + "\" ");
            out.print("lat=\"" + note.getLatLon().lat() + "\" ");
            out.print("lon=\"" + note.getLatLon().lon() + "\" ");
            out.print("created_at=\"" + ISO8601_FORMAT.format(note.getCreatedAt()) + "\" ");
            if (note.getClosedAt() != null) {
                out.print("closed_at=\"" + ISO8601_FORMAT.format(note.getClosedAt()) + "\" ");
            }

            out.println(">");
            for (NoteComment comment : note.getComments()) {
                writeComment(comment);
            }
            out.println("  </note>");
        }

        out.println("</osm-notes>");
        out.flush();
    }

    private void writeComment(NoteComment comment) {
        out.print("    <comment");
        out.print(" action=\"" + comment.getNoteAction() + "\" ");
        out.print("timestamp=\"" + ISO8601_FORMAT.format(comment.getCommentTimestamp()) + "\" ");
        if (comment.getUser() != null && !comment.getUser().equals(User.getAnonymous())) {
            out.print("uid=\"" + comment.getUser().getId() + "\" ");
            out.print("user=\"" + encode(comment.getUser().getName()) + "\" ");
        }
        out.print("is_new=\"" + comment.getIsNew() + "\" ");
        out.print(">");
        out.print(encode(comment.getText(), false));
        out.println("</comment>");
    }
}
