// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions.upload;

import java.io.IOException;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.notes.Note;
import org.openstreetmap.josm.data.notes.NoteComment;
import org.openstreetmap.josm.data.osm.NoteData;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.OsmApi;
import org.openstreetmap.josm.io.OsmTransferException;
import org.xml.sax.SAXException;

public class UploadNotesTask {

    private UploadTask uploadTask;
    private NoteData noteData;

    public void uploadNotes(NoteData noteData, ProgressMonitor progressMonitor) {
        this.noteData = noteData;
        uploadTask = new UploadTask("Uploading modified notes", progressMonitor);
        Main.worker.submit(uploadTask);
    }

    private class UploadTask extends PleaseWaitRunnable {

        private boolean isCanceled = false;

        public UploadTask(String title, ProgressMonitor monitor) {
            super(title, monitor, false);
        }

        @Override
        protected void cancel() {
            Main.debug("note upload canceled");
            isCanceled = true;
        }

        @Override
        protected void realRun() throws SAXException, IOException, OsmTransferException {
            ProgressMonitor monitor = progressMonitor.createSubTaskMonitor(ProgressMonitor.ALL_TICKS, false);
            OsmApi api = OsmApi.getOsmApi();
            for (Note note : noteData.getNotes()) {
                if(isCanceled) {
                    Main.info("Note upload interrupted by user");
                    break;
                }
                for (NoteComment comment : note.getComments()) {
                    if (comment.getIsNew()) {
                        Main.debug("found note change to upload");
                        Note newNote;
                        switch (comment.getNoteAction()) {
                        case opened:
                            Main.debug("opening new note");
                            newNote = api.createNote(note.getLatLon(), comment.getText(), monitor);
                            note.setId(newNote.getId());
                            break;
                        case closed:
                            Main.debug("closing note " + note.getId());
                            newNote = api.closeNote(note, comment.getText(), monitor);
                            break;
                        case commented:
                            Main.debug("adding comment to note " + note.getId());
                            newNote = api.addCommentToNote(note, comment.getText(), monitor);
                            break;
                        case reopened:
                            Main.debug("reopening note " + note.getId());
                            newNote = api.reopenNote(note, comment.getText(), monitor);
                            break;
                        }
                        comment.setIsNew(false);
                    }
                }
            }
        }

        @Override
        protected void finish() {
            Main.debug("finish called in notes upload task");
        }

    }

}
