// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions.downloadtasks;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Future;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.notes.Note;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.BoundingBoxDownloader;
import org.openstreetmap.josm.io.OsmServerLocationReader;
import org.openstreetmap.josm.io.OsmServerReader;
import org.openstreetmap.josm.io.OsmTransferException;
import org.xml.sax.SAXException;

/** Task for downloading notes */
public class DownloadNotesTask extends AbstractDownloadTask {

    private DownloadTask downloadTask;

    @Override
    public Future<?> download(boolean newLayer, Bounds downloadArea, ProgressMonitor progressMonitor) {
        downloadTask = new DownloadTask(new BoundingBoxDownloader(downloadArea), progressMonitor);
        return Main.worker.submit(downloadTask);
    }

    @Override
    public Future<?> loadUrl(boolean newLayer, String url, ProgressMonitor progressMonitor) {
        downloadTask = new DownloadTask(new OsmServerLocationReader(url), progressMonitor);
        return Main.worker.submit(downloadTask);
    }

    @Override
    public void cancel() {
        if (downloadTask != null) {
            downloadTask.cancel();
        }
    }

    @Override
    public String getConfirmationMessage(URL url) {
        // TODO Auto-generated method stub
        return null;
    }

    class DownloadTask extends PleaseWaitRunnable {
        private OsmServerReader reader;
        private List<Note> notesData;

        public DownloadTask(OsmServerReader reader, ProgressMonitor progressMonitor) {
            super(tr("Downloading Notes"));
            this.reader = reader;
        }

        @Override
        public void realRun() throws IOException, SAXException, OsmTransferException {
            if(isCanceled()) {
                return;
            }
            ProgressMonitor subMonitor = progressMonitor.createSubTaskMonitor(ProgressMonitor.ALL_TICKS, false);
            try {
                notesData = reader.parseNotes(null, null, subMonitor);
            } catch(Exception e) {
                if (isCanceled())
                    return;
                if (e instanceof OsmTransferException) {
                    rememberException(e);
                } else {
                    rememberException(new OsmTransferException(e));
                }
            }
        }

        @Override protected void finish() {
            Main.debug("finish called in DownloadNotesTask");
            if (isCanceled() || isFailed()) {
                Main.debug("was cancelled or failed");
                return;
            }
            if (notesData == null) {
                Main.debug("notes are null");
                return;
            }

            Main.debug("got notes: " + notesData.size());
            //TODO: import notes into layer
        }

        @Override
        protected void cancel() {
            setCanceled(true);
            if (reader != null) {
                reader.cancel();
            }
        }
    }

}
