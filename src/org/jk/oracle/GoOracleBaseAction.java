package org.jk.oracle;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class GoOracleBaseAction extends AnAction {

    /**
     * sub-command of oracle command.
     * @return sub-command of oracle.
     */
    protected abstract String command();

    private Project project;
    private Editor editor;
    private VirtualFile vf;

    private static final String ORACLE_RESULT_PATH_REGEX = "((.*):(\\d+):(\\d+)): (.*)";
    private static final Pattern ORACLE_RESULT_PATH_PATTERN = Pattern.compile(ORACLE_RESULT_PATH_REGEX);
    private Filter defaultFilter = new Filter() {
        @Nullable
        @Override
        public Result applyFilter(String line, int entireLength) {
            int textStartOffset = entireLength - line.length();
            Matcher m = ORACLE_RESULT_PATH_PATTERN.matcher(line);
            ResultItem item = null;
            List<ResultItem> items = null;
            while (m.find()) {
                int start = textStartOffset + m.start(1);
                int end = textStartOffset + m.end(2);
                String path = m.group(2);
                VirtualFile pathVF = LocalFileSystem.getInstance().findFileByIoFile(new File(path));
                int row = Integer.valueOf(m.group(3));
                int column = Integer.valueOf(m.group(4));
                if (item == null) {
                    item = new ResultItem(start, end, new OpenFileHyperlinkInfo(project, pathVF, row, column));
                } else {
                    if (items == null) {
                        items = new ArrayList<ResultItem>(2);
                        items.add(item);
                    }
                    items.add(new ResultItem(start, end, new OpenFileHyperlinkInfo(project, pathVF, row, column)));
                }
            }
            return items != null ? new Result(items)
                    : item != null ? new Result(item.getHighlightStartOffset(), item.getHighlightEndOffset(), item.getHyperlinkInfo())
                    : null;
        }
    };

    private void initMeta(AnActionEvent e) {
        this.editor = e.getData(DataKeys.EDITOR);
        this.project = this.editor.getProject();
        this.vf = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    }

    /**
     *
     * @return oracle command line args.
     */
    private String[] commandLine() {
        List<String> args = new ArrayList<String>();
        String oraclePath = getOraclePath();
        if (oraclePath == null || oraclePath.length() <= 0) {
            return null;
        }
        args.add(oraclePath);
        args.add(String.format("-pos=%s:#%d", vf.getPath(), editor.getCaretModel().getOffset()));
        String[] argsArray = new String[args.size()];
        args.add(command());
        return args.toArray(argsArray);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        initMeta(e);
        String[] args = commandLine();
        if (args == null || args.length <= 0) {
            //TODO: show information
            return;
        }
        GeneralCommandLine commandLine = new GeneralCommandLine(args);
        try {
            OSProcessHandler process = new OSProcessHandler(commandLine);
            RunContentExecutor runContentExecutor = new RunContentExecutor(project, process);
            Filter filter = Filter();
            if (filter != null) {
                runContentExecutor.withFilter(filter);
            }
            runContentExecutor.run();
        } catch (ExecutionException e1) {
            e1.printStackTrace();
        }
    }

    /**
     * return Filter used by RunContentExecutor to highlight something which
     * can be override by subclass.
     * @return filter
     */
    protected Filter Filter() {
        return getDefaultFilter();
    }

    /**
     * get oracle command path, searching from env automatically
     * @return oracle command path
     */
    protected String getOraclePath() {
        //TODO: get path from env
        return "oracle";
    }

    public Filter getDefaultFilter() {
        return defaultFilter;
    }
}
