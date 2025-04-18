/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hop.pipeline.transforms.csvinput;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.provider.local.LocalFile;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopPluginException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.file.TextFileInputField;
import org.apache.hop.core.logging.HopLogStore;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.logging.LoggingRegistry;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaBase;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.PipelinePreviewFactory;
import org.apache.hop.pipeline.engines.local.LocalPipelineEngine;
import org.apache.hop.pipeline.transform.ITransform;
import org.apache.hop.pipeline.transform.RowAdapter;
import org.apache.hop.pipeline.transforms.common.ICsvInputAwareMeta;
import org.apache.hop.pipeline.transforms.fileinput.TextFileCSVImportProgressDialog;
import org.apache.hop.staticschema.metadata.SchemaDefinition;
import org.apache.hop.staticschema.metadata.SchemaFieldDefinition;
import org.apache.hop.staticschema.util.SchemaDefinitionUtil;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.EnterNumberDialog;
import org.apache.hop.ui.core.dialog.EnterTextDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.dialog.MessageDialogWithToggle;
import org.apache.hop.ui.core.dialog.PreviewRowsDialog;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.MetaSelectionLine;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.pipeline.HopGuiPipelineGraph;
import org.apache.hop.ui.pipeline.dialog.PipelinePreviewProgressDialog;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.apache.hop.ui.pipeline.transform.common.ICsvInputAwareImportProgressDialog;
import org.apache.hop.ui.pipeline.transform.common.ICsvInputAwareTransformDialog;
import org.apache.hop.ui.pipeline.transform.common.IGetFieldsCapableTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

public class CsvInputDialog extends BaseTransformDialog
    implements IGetFieldsCapableTransformDialog<CsvInputMeta>, ICsvInputAwareTransformDialog {
  private static final Class<?> PKG = CsvInput.class;
  public static final String STRING_USAGE_INFO_PARAMETER = "UsageInfoParameter";

  private final CsvInputMeta inputMeta;

  private TextVar wFilename;
  private CCombo wFilenameField;
  private Button wbbFilename; // Browse for a file
  private Button wIncludeFilename;
  private TextVar wRowNumField;
  private TextVar wDelimiter;
  private TextVar wEnclosure;
  private TextVar wBufferSize;
  private Button wLazyConversion;
  private Button wHeaderPresent;
  private TableView wFields;
  private Button wAddResult;
  private boolean isReceivingInput;
  private Button wRunningInParallel;
  private Button wNewlinePossible;
  private ComboVar wEncoding;

  private boolean gotEncodings = false;

  private Label wlRunningInParallel;

  private boolean initializing;

  private AtomicBoolean previewBusy;

  private MetaSelectionLine<SchemaDefinition> wSchemaDefinition;

  public CsvInputDialog(
      Shell parent, IVariables variables, CsvInputMeta transformMeta, PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    inputMeta = transformMeta;
  }

  @Override
  public String open() {
    Shell parent = getParent();

    shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);
    PropsUi.setLook(shell);
    setShellImage(shell, inputMeta);

    ModifyListener lsMod = e -> inputMeta.setChanged();
    changed = inputMeta.hasChanged();

    ModifyListener lsContent = arg0 -> {};
    initializing = true;
    previewBusy = new AtomicBoolean(false);

    SelectionListener lsSelection =
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            fillFieldsLayoutFromSchema();
            inputMeta.setChanged();
          }
        };

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();

    shell.setLayout(formLayout);
    shell.setText(BaseMessages.getString(PKG, "CsvInputDialog.Shell.Title"));

    int middle = props.getMiddlePct();
    int margin = PropsUi.getMargin();

    // Transform name line
    //
    wlTransformName = new Label(shell, SWT.RIGHT);
    wlTransformName.setText(BaseMessages.getString(PKG, "CsvInputDialog.TransformName.Label"));
    PropsUi.setLook(wlTransformName);
    fdlTransformName = new FormData();
    fdlTransformName.left = new FormAttachment(0, 0);
    fdlTransformName.right = new FormAttachment(middle, -margin);
    fdlTransformName.top = new FormAttachment(0, margin);
    wlTransformName.setLayoutData(fdlTransformName);
    wTransformName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wTransformName);
    wTransformName.addModifyListener(lsMod);
    fdTransformName = new FormData();
    fdTransformName.left = new FormAttachment(middle, 0);
    fdTransformName.top = new FormAttachment(0, margin);
    fdTransformName.right = new FormAttachment(100, 0);
    wTransformName.setLayoutData(fdTransformName);
    Control lastControl = wTransformName;

    // See if the transform receives input. If so, we don't ask for the filename, but
    // for the filename field.
    //
    isReceivingInput = !pipelineMeta.findPreviousTransforms(transformMeta).isEmpty();
    if (isReceivingInput) {

      IRowMeta previousFields;
      try {
        previousFields = pipelineMeta.getPrevTransformFields(variables, transformMeta);
      } catch (HopTransformException e) {
        new ErrorDialog(
            shell,
            BaseMessages.getString(PKG, "CsvInputDialog.ErrorDialog.UnableToGetInputFields.Title"),
            BaseMessages.getString(
                PKG, "CsvInputDialog.ErrorDialog.UnableToGetInputFields.Message"),
            e);
        previousFields = new RowMeta();
      }

      // The filename field ...
      //
      Label wlFilename = new Label(shell, SWT.RIGHT);
      wlFilename.setText(BaseMessages.getString(PKG, "CsvInputDialog.FilenameField.Label"));
      PropsUi.setLook(wlFilename);
      FormData fdlFilename = new FormData();
      fdlFilename.top = new FormAttachment(lastControl, margin);
      fdlFilename.left = new FormAttachment(0, 0);
      fdlFilename.right = new FormAttachment(middle, -margin);
      wlFilename.setLayoutData(fdlFilename);
      wFilenameField = new CCombo(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
      wFilenameField.setItems(previousFields.getFieldNames());
      PropsUi.setLook(wFilenameField);
      wFilenameField.addModifyListener(lsMod);
      FormData fdFilename = new FormData();
      fdFilename.top = new FormAttachment(lastControl, margin);
      fdFilename.left = new FormAttachment(middle, 0);
      fdFilename.right = new FormAttachment(100, 0);
      wFilenameField.setLayoutData(fdFilename);
      lastControl = wFilenameField;

      // Checkbox to include the filename in the output...
      //
      Label wlIncludeFilename = new Label(shell, SWT.RIGHT);
      wlIncludeFilename.setText(
          BaseMessages.getString(PKG, "CsvInputDialog.IncludeFilenameField.Label"));
      PropsUi.setLook(wlIncludeFilename);
      FormData fdlIncludeFilename = new FormData();
      fdlIncludeFilename.top = new FormAttachment(lastControl, margin);
      fdlIncludeFilename.left = new FormAttachment(0, 0);
      fdlIncludeFilename.right = new FormAttachment(middle, -margin);
      wlIncludeFilename.setLayoutData(fdlIncludeFilename);
      wIncludeFilename = new Button(shell, SWT.CHECK);
      PropsUi.setLook(wIncludeFilename);
      wFilenameField.addModifyListener(lsMod);
      FormData fdIncludeFilename = new FormData();
      fdIncludeFilename.top = new FormAttachment(wlIncludeFilename, 0, SWT.CENTER);
      fdIncludeFilename.left = new FormAttachment(middle, 0);
      fdIncludeFilename.right = new FormAttachment(100, 0);
      wIncludeFilename.setLayoutData(fdIncludeFilename);
      lastControl = wIncludeFilename;
    } else {

      // Filename...
      //
      // The filename browse button
      //
      wbbFilename = new Button(shell, SWT.PUSH | SWT.CENTER);
      PropsUi.setLook(wbbFilename);
      wbbFilename.setText(BaseMessages.getString(PKG, "System.Button.Browse"));
      wbbFilename.setToolTipText(
          BaseMessages.getString(PKG, "System.Tooltip.BrowseForFileOrDirAndAdd"));
      FormData fdbFilename = new FormData();
      fdbFilename.top = new FormAttachment(lastControl, margin);
      fdbFilename.right = new FormAttachment(100, 0);
      wbbFilename.setLayoutData(fdbFilename);

      // The field itself...
      //
      Label wlFilename = new Label(shell, SWT.RIGHT);
      wlFilename.setText(BaseMessages.getString(PKG, "CsvInputDialog.Filename.Label"));
      PropsUi.setLook(wlFilename);
      FormData fdlFilename = new FormData();
      fdlFilename.top = new FormAttachment(lastControl, margin);
      fdlFilename.left = new FormAttachment(0, 0);
      fdlFilename.right = new FormAttachment(middle, -margin);
      wlFilename.setLayoutData(fdlFilename);
      wFilename = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
      PropsUi.setLook(wFilename);
      wFilename.addModifyListener(lsMod);
      FormData fdFilename = new FormData();
      fdFilename.top = new FormAttachment(lastControl, margin);
      fdFilename.left = new FormAttachment(middle, 0);
      fdFilename.right = new FormAttachment(wbbFilename, -margin);
      wFilename.setLayoutData(fdFilename);
      lastControl = wFilename;
    }

    // delimiter
    Label wlDelimiter = new Label(shell, SWT.RIGHT);
    wlDelimiter.setText(BaseMessages.getString(PKG, "CsvInputDialog.Delimiter.Label"));
    PropsUi.setLook(wlDelimiter);
    FormData fdlDelimiter = new FormData();
    fdlDelimiter.top = new FormAttachment(lastControl, margin);
    fdlDelimiter.left = new FormAttachment(0, 0);
    fdlDelimiter.right = new FormAttachment(middle, -margin);
    wlDelimiter.setLayoutData(fdlDelimiter);
    Button wbDelimiter = new Button(shell, SWT.PUSH | SWT.CENTER);
    PropsUi.setLook(wbDelimiter);
    wbDelimiter.setText(BaseMessages.getString(PKG, "CsvInputDialog.Delimiter.Button"));
    FormData fdbDelimiter = new FormData();
    fdbDelimiter.top = new FormAttachment(lastControl, margin);
    fdbDelimiter.right = new FormAttachment(100, 0);
    wbDelimiter.setLayoutData(fdbDelimiter);
    wDelimiter = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDelimiter);
    wDelimiter.addModifyListener(lsMod);
    FormData fdDelimiter = new FormData();
    fdDelimiter.top = new FormAttachment(lastControl, margin);
    fdDelimiter.left = new FormAttachment(middle, 0);
    fdDelimiter.right = new FormAttachment(wbDelimiter, -margin);
    wDelimiter.setLayoutData(fdDelimiter);
    wDelimiter.addModifyListener(lsContent);
    lastControl = wDelimiter;

    // enclosure
    Label wlEnclosure = new Label(shell, SWT.RIGHT);
    wlEnclosure.setText(BaseMessages.getString(PKG, "CsvInputDialog.Enclosure.Label"));
    PropsUi.setLook(wlEnclosure);
    FormData fdlEnclosure = new FormData();
    fdlEnclosure.top = new FormAttachment(lastControl, margin);
    fdlEnclosure.left = new FormAttachment(0, 0);
    fdlEnclosure.right = new FormAttachment(middle, -margin);
    wlEnclosure.setLayoutData(fdlEnclosure);
    wEnclosure = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wEnclosure);
    wEnclosure.addModifyListener(lsMod);
    FormData fdEnclosure = new FormData();
    fdEnclosure.top = new FormAttachment(lastControl, margin);
    fdEnclosure.left = new FormAttachment(middle, 0);
    fdEnclosure.right = new FormAttachment(100, 0);
    wEnclosure.setLayoutData(fdEnclosure);
    wEnclosure.addModifyListener(lsContent);
    lastControl = wEnclosure;

    // bufferSize
    //
    Label wlBufferSize = new Label(shell, SWT.RIGHT);
    wlBufferSize.setText(BaseMessages.getString(PKG, "CsvInputDialog.BufferSize.Label"));
    PropsUi.setLook(wlBufferSize);
    FormData fdlBufferSize = new FormData();
    fdlBufferSize.top = new FormAttachment(lastControl, margin);
    fdlBufferSize.left = new FormAttachment(0, 0);
    fdlBufferSize.right = new FormAttachment(middle, -margin);
    wlBufferSize.setLayoutData(fdlBufferSize);
    wBufferSize = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wBufferSize);
    wBufferSize.addModifyListener(lsMod);
    FormData fdBufferSize = new FormData();
    fdBufferSize.top = new FormAttachment(lastControl, margin);
    fdBufferSize.left = new FormAttachment(middle, 0);
    fdBufferSize.right = new FormAttachment(100, 0);
    wBufferSize.setLayoutData(fdBufferSize);
    lastControl = wBufferSize;

    // performingLazyConversion?
    //
    Label wlLazyConversion = new Label(shell, SWT.RIGHT);
    wlLazyConversion.setText(BaseMessages.getString(PKG, "CsvInputDialog.LazyConversion.Label"));
    PropsUi.setLook(wlLazyConversion);
    FormData fdlLazyConversion = new FormData();
    fdlLazyConversion.top = new FormAttachment(lastControl, margin);
    fdlLazyConversion.left = new FormAttachment(0, 0);
    fdlLazyConversion.right = new FormAttachment(middle, -margin);
    wlLazyConversion.setLayoutData(fdlLazyConversion);
    wLazyConversion = new Button(shell, SWT.CHECK);
    PropsUi.setLook(wLazyConversion);
    FormData fdLazyConversion = new FormData();
    fdLazyConversion.top = new FormAttachment(wlLazyConversion, 0, SWT.CENTER);
    fdLazyConversion.left = new FormAttachment(middle, 0);
    fdLazyConversion.right = new FormAttachment(100, 0);
    wLazyConversion.setLayoutData(fdLazyConversion);
    lastControl = wlLazyConversion;

    // header row?
    //
    Label wlHeaderPresent = new Label(shell, SWT.RIGHT);
    wlHeaderPresent.setText(BaseMessages.getString(PKG, "CsvInputDialog.HeaderPresent.Label"));
    PropsUi.setLook(wlHeaderPresent);
    FormData fdlHeaderPresent = new FormData();
    fdlHeaderPresent.top = new FormAttachment(lastControl, margin);
    fdlHeaderPresent.left = new FormAttachment(0, 0);
    fdlHeaderPresent.right = new FormAttachment(middle, -margin);
    wlHeaderPresent.setLayoutData(fdlHeaderPresent);
    wHeaderPresent = new Button(shell, SWT.CHECK);
    PropsUi.setLook(wHeaderPresent);
    FormData fdHeaderPresent = new FormData();
    fdHeaderPresent.top = new FormAttachment(wlHeaderPresent, 0, SWT.CENTER);
    fdHeaderPresent.left = new FormAttachment(middle, 0);
    fdHeaderPresent.right = new FormAttachment(100, 0);
    wHeaderPresent.setLayoutData(fdHeaderPresent);
    lastControl = wlHeaderPresent;

    Label wlAddResult = new Label(shell, SWT.RIGHT);
    wlAddResult.setText(BaseMessages.getString(PKG, "CsvInputDialog.AddResult.Label"));
    PropsUi.setLook(wlAddResult);
    FormData fdlAddResult = new FormData();
    fdlAddResult.left = new FormAttachment(0, 0);
    fdlAddResult.top = new FormAttachment(lastControl, margin);
    fdlAddResult.right = new FormAttachment(middle, -margin);
    wlAddResult.setLayoutData(fdlAddResult);
    wAddResult = new Button(shell, SWT.CHECK);
    PropsUi.setLook(wAddResult);
    wAddResult.setToolTipText(BaseMessages.getString(PKG, "CsvInputDialog.AddResult.Tooltip"));
    FormData fdAddResult = new FormData();
    fdAddResult.left = new FormAttachment(middle, 0);
    fdAddResult.top = new FormAttachment(wlAddResult, 0, SWT.CENTER);
    wAddResult.setLayoutData(fdAddResult);
    lastControl = wlAddResult;

    // The field itself...
    //
    Label wlRowNumField = new Label(shell, SWT.RIGHT);
    wlRowNumField.setText(BaseMessages.getString(PKG, "CsvInputDialog.RowNumField.Label"));
    PropsUi.setLook(wlRowNumField);
    FormData fdlRowNumField = new FormData();
    fdlRowNumField.top = new FormAttachment(lastControl, margin);
    fdlRowNumField.left = new FormAttachment(0, 0);
    fdlRowNumField.right = new FormAttachment(middle, -margin);
    wlRowNumField.setLayoutData(fdlRowNumField);
    wRowNumField = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wRowNumField);
    wRowNumField.addModifyListener(lsMod);
    FormData fdRowNumField = new FormData();
    fdRowNumField.top = new FormAttachment(wlRowNumField, 0, SWT.CENTER);
    fdRowNumField.left = new FormAttachment(middle, 0);
    fdRowNumField.right = new FormAttachment(100, 0);
    wRowNumField.setLayoutData(fdRowNumField);
    lastControl = wlRowNumField;

    // running in parallel?
    //
    wlRunningInParallel = new Label(shell, SWT.RIGHT);
    wlRunningInParallel.setText(
        BaseMessages.getString(PKG, "CsvInputDialog.RunningInParallel.Label"));
    PropsUi.setLook(wlRunningInParallel);
    FormData fdlRunningInParallel = new FormData();
    fdlRunningInParallel.top = new FormAttachment(lastControl, margin);
    fdlRunningInParallel.left = new FormAttachment(0, 0);
    fdlRunningInParallel.right = new FormAttachment(middle, -margin);
    wlRunningInParallel.setLayoutData(fdlRunningInParallel);
    wRunningInParallel = new Button(shell, SWT.CHECK);
    PropsUi.setLook(wRunningInParallel);
    FormData fdRunningInParallel = new FormData();
    fdRunningInParallel.top = new FormAttachment(wlRunningInParallel, 0, SWT.CENTER);
    fdRunningInParallel.left = new FormAttachment(middle, 0);
    wRunningInParallel.setLayoutData(fdRunningInParallel);
    lastControl = wlRunningInParallel;

    // Is a new line possible in a field?
    //
    Label wlNewlinePossible = new Label(shell, SWT.RIGHT);
    wlNewlinePossible.setText(BaseMessages.getString(PKG, "CsvInputDialog.NewlinePossible.Label"));
    PropsUi.setLook(wlNewlinePossible);
    FormData fdlNewlinePossible = new FormData();
    fdlNewlinePossible.top = new FormAttachment(lastControl, margin);
    fdlNewlinePossible.left = new FormAttachment(0, 0);
    fdlNewlinePossible.right = new FormAttachment(middle, -margin);
    wlNewlinePossible.setLayoutData(fdlNewlinePossible);
    wNewlinePossible = new Button(shell, SWT.CHECK);
    PropsUi.setLook(wNewlinePossible);
    FormData fdNewlinePossible = new FormData();
    fdNewlinePossible.top = new FormAttachment(wlNewlinePossible, 0, SWT.CENTER);
    fdNewlinePossible.left = new FormAttachment(middle, 0);
    wNewlinePossible.setLayoutData(fdNewlinePossible);
    wNewlinePossible.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent event) {
            setFlags();
          }
        });
    wNewlinePossible.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent arg0) {
            asyncUpdatePreview();
          }
        });
    lastControl = wlNewlinePossible;

    // Encoding
    Label wlEncoding = new Label(shell, SWT.RIGHT);
    wlEncoding.setText(BaseMessages.getString(PKG, "CsvInputDialog.Encoding.Label"));
    PropsUi.setLook(wlEncoding);
    FormData fdlEncoding = new FormData();
    fdlEncoding.top = new FormAttachment(lastControl, margin);
    fdlEncoding.left = new FormAttachment(0, 0);
    fdlEncoding.right = new FormAttachment(middle, -margin);
    wlEncoding.setLayoutData(fdlEncoding);
    wEncoding = new ComboVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wEncoding);
    wEncoding.addModifyListener(lsMod);
    FormData fdEncoding = new FormData();
    fdEncoding.top = new FormAttachment(wlEncoding, 0, SWT.CENTER);
    fdEncoding.left = new FormAttachment(middle, 0);
    fdEncoding.right = new FormAttachment(100, 0);
    wEncoding.setLayoutData(fdEncoding);
    wEncoding.addModifyListener(lsContent);
    lastControl = wEncoding;

    wEncoding.addFocusListener(
        new FocusListener() {
          @Override
          public void focusLost(FocusEvent e) {
            // Do nothing
          }

          @Override
          public void focusGained(FocusEvent e) {
            Cursor busy = new Cursor(shell.getDisplay(), SWT.CURSOR_WAIT);
            shell.setCursor(busy);
            setEncodings();
            shell.setCursor(null);
            busy.dispose();
          }
        });

    // Add schema definition line
    wSchemaDefinition =
        new MetaSelectionLine<>(
            variables,
            metadataProvider,
            SchemaDefinition.class,
            shell,
            SWT.NONE,
            BaseMessages.getString(PKG, "CsvInputDialog.SchemaDefinition.Label"),
            BaseMessages.getString(PKG, "CsvInputDialog.SchemaDefinition.Tooltip"));

    PropsUi.setLook(wSchemaDefinition);
    FormData fdSchemaDefinition = new FormData();
    fdSchemaDefinition.left = new FormAttachment(0, 0);
    fdSchemaDefinition.top = new FormAttachment(wEncoding, margin);
    fdSchemaDefinition.right = new FormAttachment(100, 0);
    wSchemaDefinition.setLayoutData(fdSchemaDefinition);

    try {
      wSchemaDefinition.fillItems();
    } catch (Exception e) {
      log.logError("Error getting schema definition items", e);
    }

    wSchemaDefinition.addSelectionListener(lsSelection);

    // Some buttons first, so that the dialog scales nicely...
    //
    wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wPreview = new Button(shell, SWT.PUSH);
    wPreview.setText(BaseMessages.getString(PKG, "System.Button.Preview"));
    wPreview.setEnabled(!isReceivingInput);
    wGet = new Button(shell, SWT.PUSH);
    wGet.setText(BaseMessages.getString(PKG, "System.Button.GetFields"));
    wGet.setEnabled(!isReceivingInput);

    setButtonPositions(new Button[] {wOk, wGet, wPreview, wCancel}, margin, null);

    // Fields
    ColumnInfo[] colinf =
        new ColumnInfo[] {
          new ColumnInfo(
              BaseMessages.getString(PKG, "CsvInputDialog.NameColumn.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "CsvInputDialog.TypeColumn.Column"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              ValueMetaFactory.getValueMetaNames(),
              true),
          new ColumnInfo(
              BaseMessages.getString(PKG, "CsvInputDialog.FormatColumn.Column"),
              ColumnInfo.COLUMN_TYPE_FORMAT,
              2),
          new ColumnInfo(
              BaseMessages.getString(PKG, "CsvInputDialog.LengthColumn.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "CsvInputDialog.PrecisionColumn.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "CsvInputDialog.CurrencyColumn.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "CsvInputDialog.DecimalColumn.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "CsvInputDialog.GroupColumn.Column"),
              ColumnInfo.COLUMN_TYPE_TEXT,
              false),
          new ColumnInfo(
              BaseMessages.getString(PKG, "CsvInputDialog.TrimTypeColumn.Column"),
              ColumnInfo.COLUMN_TYPE_CCOMBO,
              ValueMetaBase.trimTypeDesc),
        };

    wFields =
        new TableView(variables, shell, SWT.FULL_SELECTION | SWT.MULTI, colinf, 1, lsMod, props);

    FormData fdFields = new FormData();
    fdFields.top = new FormAttachment(lastControl, margin * 2);
    fdFields.bottom = new FormAttachment(wOk, -margin * 2);
    fdFields.left = new FormAttachment(0, 0);
    fdFields.right = new FormAttachment(100, 0);
    wFields.setLayoutData(fdFields);
    wFields.setContentListener(lsContent);

    wCancel.addListener(SWT.Selection, e -> cancel());
    wOk.addListener(SWT.Selection, e -> ok());
    wPreview.addListener(SWT.Selection, e -> preview());
    wGet.addListener(SWT.Selection, e -> getFields());

    // Allow the insertion of tabs as separator...
    wbDelimiter.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent se) {
            Text t = wDelimiter.getTextWidget();
            if (t != null) {
              t.insert("\t");
            }
          }
        });

    if (wbbFilename != null) {
      // Listen to the browse button next to the file name
      //
      wbbFilename.addListener(
          SWT.Selection,
          e ->
              BaseDialog.presentFileDialog(
                  shell,
                  wFilename,
                  variables,
                  new String[] {"*.txt;*.csv", "*.csv", "*.txt", "*"},
                  new String[] {
                    BaseMessages.getString(PKG, "System.FileType.CSVFiles")
                        + ", "
                        + BaseMessages.getString(PKG, "System.FileType.TextFiles"),
                    BaseMessages.getString(PKG, "System.FileType.CSVFiles"),
                    BaseMessages.getString(PKG, "System.FileType.TextFiles"),
                    BaseMessages.getString(PKG, "System.FileType.AllFiles")
                  },
                  false));
    }

    getData();

    inputMeta.setChanged(changed);
    initializing = false;

    shell
        .getDisplay()
        .asyncExec(
            () -> {
              // Show more information about the transform and its alternatives
              //
              if ("Y"
                  .equalsIgnoreCase(props.getCustomParameter(STRING_USAGE_INFO_PARAMETER, "Y"))) {
                MessageDialogWithToggle md =
                    new MessageDialogWithToggle(
                        shell,
                        BaseMessages.getString(PKG, "CsvInputDialog.UsageInfoDialog.DialogTitle"),
                        BaseMessages.getString(
                                PKG, "CsvInputDialog.UsageInfoDialog.DialogMessage", Const.CR)
                            + Const.CR,
                        SWT.ICON_INFORMATION,
                        new String[] {
                          BaseMessages.getString(PKG, "CsvInputDialog.UsageInfoDialog.Close")
                        },
                        BaseMessages.getString(
                            PKG, "CsvInputDialog.UsageInfoDialog.DoNotShowAgain"),
                        "N"
                            .equalsIgnoreCase(
                                props.getCustomParameter(STRING_USAGE_INFO_PARAMETER, "Y")));
                md.open();
                props.setCustomParameter(
                    STRING_USAGE_INFO_PARAMETER, md.getToggleState() ? "N" : "Y");
              }
            });

    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());

    return transformName;
  }

  private void fillFieldsLayoutFromSchema() {

    if (!wSchemaDefinition.isDisposed()) {
      final String schemaName = wSchemaDefinition.getText();

      MessageBox mb = new MessageBox(shell, SWT.ICON_QUESTION | SWT.NO | SWT.YES);
      mb.setMessage(
          BaseMessages.getString(PKG, "CsvInputDialog.Load.SchemaDefinition.Message", schemaName));
      mb.setText(BaseMessages.getString(PKG, "CsvInputDialog.Load.SchemaDefinition.Title"));
      int answer = mb.open();

      if (answer == SWT.YES && !Utils.isEmpty(schemaName)) {
        try {
          SchemaDefinition schemaDefinition =
              (new SchemaDefinitionUtil()).loadSchemaDefinition(metadataProvider, schemaName);
          if (schemaDefinition != null) {
            IRowMeta r = schemaDefinition.getRowMeta();
            if (r != null) {
              String[] fieldNames = r.getFieldNames();
              if (fieldNames != null) {
                wFields.clearAll();
                for (int i = 0; i < fieldNames.length; i++) {
                  IValueMeta valueMeta = r.getValueMeta(i);
                  final TableItem item = getTableItem(valueMeta.getName(), true);
                  int colnr = 1;
                  item.setText(colnr++, valueMeta.getName());
                  item.setText(colnr++, ValueMetaFactory.getValueMetaName(valueMeta.getType()));
                  item.setText(colnr++, Const.NVL(valueMeta.getConversionMask(), ""));
                  item.setText(
                      colnr++,
                      valueMeta.getLength() >= 0 ? Integer.toString(valueMeta.getLength()) : "");
                  item.setText(
                      colnr++,
                      valueMeta.getPrecision() >= 0
                          ? Integer.toString(valueMeta.getPrecision())
                          : "");
                  final SchemaFieldDefinition schemaFieldDefinition =
                      schemaDefinition.getFieldDefinitions().get(i);
                  item.setText(colnr++, Const.NVL(schemaFieldDefinition.getCurrencySymbol(), ""));
                  item.setText(colnr++, Const.NVL(schemaFieldDefinition.getDecimalSymbol(), ""));
                  item.setText(colnr++, Const.NVL(schemaFieldDefinition.getGroupingSymbol(), ""));
                  item.setText(
                      colnr++,
                      Const.NVL(ValueMetaBase.getTrimTypeDesc(valueMeta.getTrimType()), ""));
                }
              }
            }
          }
        } catch (HopTransformException | HopPluginException e) {

          // ignore any errors here.
        }

        wFields.removeEmptyRows();
        wFields.setRowNums();
        wFields.optWidth(true);
      }
    }
  }

  protected void setFlags() {
    // In case there are newlines in fields, we can't load data in parallel
    //
    boolean parallelPossible = !wNewlinePossible.getSelection();
    wlRunningInParallel.setEnabled(parallelPossible);
    wRunningInParallel.setEnabled(parallelPossible);
    if (!parallelPossible) {
      wRunningInParallel.setSelection(false);
    }
  }

  private void setEncodings() {
    // Encoding of the text file:
    if (!gotEncodings) {
      gotEncodings = true;

      wEncoding.removeAll();
      List<Charset> values = new ArrayList<>(Charset.availableCharsets().values());
      for (Charset charSet : values) {
        wEncoding.add(charSet.displayName());
      }

      // Now select the default!
      String defEncoding = Const.getEnvironmentVariable("file.encoding", "UTF-8");
      int idx = Const.indexOfString(defEncoding, wEncoding.getItems());
      if (idx >= 0) {
        wEncoding.select(idx);
      }
    }
  }

  @Override
  public CsvInputMeta getNewMetaInstance() {
    return new CsvInputMeta();
  }

  @Override
  public void populateMeta(CsvInputMeta inputMeta) {
    getInfo(inputMeta);
  }

  public void getData() {
    getData(inputMeta, true);
  }

  /** Copy information from the meta-data input to the dialog fields. */
  public void getData(CsvInputMeta inputMeta, boolean copyTransformName) {
    getData(inputMeta, copyTransformName, true, null);
  }

  @Override
  public void getData(
      final CsvInputMeta inputMeta,
      final boolean copyTransformName,
      final boolean reloadAllFields,
      final List<String> newFieldNames) {
    if (copyTransformName) {
      wTransformName.setText(transformName);
    }
    if (isReceivingInput) {
      wFilenameField.setText(Const.NVL(inputMeta.getFilenameField(), ""));
      wIncludeFilename.setSelection(inputMeta.isIncludingFilename());
    } else {
      wFilename.setText(Const.NVL(inputMeta.getFilename(), ""));
    }
    wDelimiter.setText(Const.NVL(inputMeta.getDelimiter(), ""));
    wEnclosure.setText(Const.NVL(inputMeta.getEnclosure(), ""));
    wBufferSize.setText(Const.NVL(inputMeta.getBufferSize(), ""));
    wLazyConversion.setSelection(inputMeta.isLazyConversionActive());
    wHeaderPresent.setSelection(inputMeta.isHeaderPresent());
    wRunningInParallel.setSelection(inputMeta.isRunningInParallel());
    wNewlinePossible.setSelection(inputMeta.isNewlinePossibleInFields());
    wRowNumField.setText(Const.NVL(inputMeta.getRowNumField(), ""));
    wAddResult.setSelection(inputMeta.isAddResultFile());
    wEncoding.setText(Const.NVL(inputMeta.getEncoding(), ""));
    wSchemaDefinition.setText(Const.NVL(inputMeta.getSchemaDefinition(), ""));

    final List<String> fieldName = newFieldNames == null ? new ArrayList() : newFieldNames;
    for (int i = 0; i < inputMeta.getInputFields().length; i++) {
      TextFileInputField field = inputMeta.getInputFields()[i];
      final TableItem item = getTableItem(field.getName(), true);
      // update the item only if we are reloading all fields, or the field is new
      if (!reloadAllFields && !fieldName.contains(field.getName())) {
        continue;
      }
      int colnr = 1;
      item.setText(colnr++, Const.NVL(field.getName(), ""));
      item.setText(colnr++, ValueMetaFactory.getValueMetaName(field.getType()));
      item.setText(colnr++, Const.NVL(field.getFormat(), ""));
      item.setText(colnr++, field.getLength() >= 0 ? Integer.toString(field.getLength()) : "");
      item.setText(
          colnr++, field.getPrecision() >= 0 ? Integer.toString(field.getPrecision()) : "");
      item.setText(colnr++, Const.NVL(field.getCurrencySymbol(), ""));
      item.setText(colnr++, Const.NVL(field.getDecimalSymbol(), ""));
      item.setText(colnr++, Const.NVL(field.getGroupSymbol(), ""));
      item.setText(colnr++, Const.NVL(field.getTrimTypeDesc(), ""));
    }
    wFields.removeEmptyRows();
    wFields.setRowNums();
    wFields.optWidth(true);

    setFlags();

    wTransformName.selectAll();
    wTransformName.setFocus();
  }

  private void cancel() {
    transformName = null;
    inputMeta.setChanged(changed);
    dispose();
  }

  private void getInfo(CsvInputMeta inputMeta) {

    if (isReceivingInput) {
      inputMeta.setFilenameField(wFilenameField.getText());
      inputMeta.setIncludingFilename(wIncludeFilename.getSelection());
    } else {
      inputMeta.setFilename(wFilename.getText());
    }

    inputMeta.setDelimiter(wDelimiter.getText());
    inputMeta.setEnclosure(wEnclosure.getText());
    inputMeta.setBufferSize(wBufferSize.getText());
    inputMeta.setLazyConversionActive(wLazyConversion.getSelection());
    inputMeta.setHeaderPresent(wHeaderPresent.getSelection());
    inputMeta.setRowNumField(wRowNumField.getText());
    inputMeta.setAddResultFile(wAddResult.getSelection());
    inputMeta.setRunningInParallel(wRunningInParallel.getSelection());
    inputMeta.setNewlinePossibleInFields(wNewlinePossible.getSelection());
    inputMeta.setEncoding(wEncoding.getText());
    inputMeta.setSchemaDefinition(wSchemaDefinition.getText());

    int nrNonEmptyFields = wFields.nrNonEmpty();
    inputMeta.allocate(nrNonEmptyFields);

    for (int i = 0; i < nrNonEmptyFields; i++) {
      TableItem item = wFields.getNonEmpty(i);

      inputMeta.getInputFields()[i] = new TextFileInputField();

      int colnr = 1;
      inputMeta.getInputFields()[i].setName(item.getText(colnr++));
      inputMeta.getInputFields()[i].setType(
          ValueMetaFactory.getIdForValueMeta(item.getText(colnr++)));
      inputMeta.getInputFields()[i].setFormat(item.getText(colnr++));
      inputMeta.getInputFields()[i].setLength(Const.toInt(item.getText(colnr++), -1));
      inputMeta.getInputFields()[i].setPrecision(Const.toInt(item.getText(colnr++), -1));
      inputMeta.getInputFields()[i].setCurrencySymbol(item.getText(colnr++));
      inputMeta.getInputFields()[i].setDecimalSymbol(item.getText(colnr++));
      inputMeta.getInputFields()[i].setGroupSymbol(item.getText(colnr++));
      inputMeta.getInputFields()[i].setTrimType(
          ValueMetaBase.getTrimTypeByDesc(item.getText(colnr++)));
    }
    wFields.removeEmptyRows();
    wFields.setRowNums();
    wFields.optWidth(true);

    inputMeta.setChanged();
  }

  private void ok() {
    if (Utils.isEmpty(wTransformName.getText())) {
      return;
    }

    getInfo(inputMeta);
    transformName = wTransformName.getText();

    dispose();
  }

  /**
   * ' Returns the {@link InputStreamReader} corresponding to the csv file, or null if the file
   * cannot be read.
   *
   * @return the {@link InputStreamReader} corresponding to the csv file, or null if the file cannot
   *     be read
   */
  private InputStreamReader getReader(final CsvInputMeta meta, final InputStream inputStream) {
    InputStreamReader reader = null;
    try {
      String filename = variables.resolve(meta.getFilename());

      FileObject fileObject = HopVfs.getFileObject(filename, variables);
      if (!(fileObject instanceof LocalFile)) {
        // We can only use NIO on local files at the moment, so that's what we
        // limit ourselves to.
        //
        throw new HopException(
            BaseMessages.getString(PKG, "CsvInput.Log.OnlyLocalFilesAreSupported"));
      }

      String realEncoding = variables.resolve(meta.getEncoding());
      if (Utils.isEmpty(realEncoding)) {
        reader = new InputStreamReader(inputStream);
      } else {
        reader = new InputStreamReader(inputStream, realEncoding);
      }
    } catch (final Exception e) {
      logError(BaseMessages.getString(PKG, "CsvInputDialog.ErrorGettingFileDesc.DialogMessage"), e);
    }
    return reader;
  }

  /**
   * ' Returns the {@link InputStream} corresponding to the csv file, or null if the file cannot be
   * read.
   *
   * @return the {@link InputStream} corresponding to the csv file, or null if the file cannot be
   *     read
   */
  private InputStream getInputStream(final CsvInputMeta meta) {
    InputStream inputStream = null;
    try {
      final String filename = variables.resolve(meta.getFilename());

      final FileObject fileObject = HopVfs.getFileObject(filename, variables);
      if (!(fileObject instanceof LocalFile)) {
        // We can only use NIO on local files at the moment, so that's what we
        // limit ourselves to.
        //
        throw new HopException(
            BaseMessages.getString(PKG, "CsvInput.Log.OnlyLocalFilesAreSupported"));
      }

      inputStream = HopVfs.getInputStream(fileObject);
    } catch (final Exception e) {
      logError(BaseMessages.getString(PKG, "CsvInputDialog.ErrorGettingFileDesc.DialogMessage"), e);
    }
    return inputStream;
  }

  @Override
  public String[] getFieldNames(final CsvInputMeta meta) {
    return getFieldNames((ICsvInputAwareMeta) meta);
  }

  @Override
  public TableView getFieldsTable() {
    return this.wFields;
  }

  @Override
  public String loadFieldsImpl(final CsvInputMeta meta, final int samples) {
    return loadFieldsImpl((ICsvInputAwareMeta) meta, samples);
  }

  // Preview the data
  private synchronized void preview() {
    // Create the XML input transform
    CsvInputMeta oneMeta = new CsvInputMeta();
    getInfo(oneMeta);

    PipelineMeta previewMeta =
        PipelinePreviewFactory.generatePreviewPipeline(
            pipelineMeta.getMetadataProvider(), oneMeta, wTransformName.getText());

    EnterNumberDialog numberDialog =
        new EnterNumberDialog(
            shell,
            props.getDefaultPreviewSize(),
            BaseMessages.getString(PKG, "CsvInputDialog.PreviewSize.DialogTitle"),
            BaseMessages.getString(PKG, "CsvInputDialog.PreviewSize.DialogMessage"));
    int previewSize = numberDialog.open();
    if (previewSize > 0) {
      PipelinePreviewProgressDialog progressDialog =
          new PipelinePreviewProgressDialog(
              shell,
              variables,
              previewMeta,
              new String[] {wTransformName.getText()},
              new int[] {previewSize});
      progressDialog.open();

      Pipeline pipeline = progressDialog.getPipeline();
      String loggingText = progressDialog.getLoggingText();

      if (!progressDialog.isCancelled()
          && pipeline.getResult() != null
          && pipeline.getResult().getNrErrors() > 0) {
        EnterTextDialog etd =
            new EnterTextDialog(
                shell,
                BaseMessages.getString(PKG, "System.Dialog.PreviewError.Title"),
                BaseMessages.getString(PKG, "System.Dialog.PreviewError.Message"),
                loggingText,
                true);
        etd.setReadOnly();
        etd.open();
      }

      PreviewRowsDialog prd =
          new PreviewRowsDialog(
              shell,
              variables,
              SWT.NONE,
              wTransformName.getText(),
              progressDialog.getPreviewRowsMeta(wTransformName.getText()),
              progressDialog.getPreviewRows(wTransformName.getText()),
              loggingText);
      prd.open();
    }
  }

  /** Load metadata from transform window */
  protected void updatePreview() {
    if (initializing) {
      return;
    }
    if (previewBusy.get()) {
      return;
    }
    try {
      previewBusy.set(true);

      CsvInputMeta meta = new CsvInputMeta();
      getInfo(meta);

      // Validate some basic data...
      //
      if (Utils.isEmpty(meta.getFilename())) {
        return;
      }
      if (Utils.isEmpty(meta.getInputFields())) {
        return;
      }

      String transformName = wTransformName.getText();

      StringBuffer buffer = new StringBuffer();
      final List<Object[]> rowsData = new ArrayList<>();
      final IRowMeta rowMeta = new RowMeta();

      try {

        meta.getFields(rowMeta, transformName, null, null, variables, metadataProvider);

        PipelineMeta previewPipelineMeta =
            PipelinePreviewFactory.generatePreviewPipeline(
                pipelineMeta.getMetadataProvider(), meta, transformName);
        final Pipeline pipeline = new LocalPipelineEngine(previewPipelineMeta);
        pipeline.prepareExecution();
        ITransform transform = pipeline.getRunThread(transformName, 0);
        transform.addRowListener(
            new RowAdapter() {
              @Override
              public void rowWrittenEvent(IRowMeta rowMeta, Object[] row)
                  throws HopTransformException {
                rowsData.add(row);

                // If we have enough rows we can stop
                //
                if (rowsData.size() > PropsUi.getInstance().getDefaultPreviewSize()) {
                  pipeline.stopAll();
                }
              }
            });
        pipeline.startThreads();
        pipeline.waitUntilFinished();
        if (pipeline.getErrors() > 0) {
          StringBuffer log = HopLogStore.getAppender().getBuffer(pipeline.getLogChannelId(), false);
          buffer.append(log);
        }
        HopLogStore.discardLines(pipeline.getLogChannelId(), false);
        LoggingRegistry.getInstance().removeIncludingChildren(pipeline.getLogChannelId());

      } catch (Exception e) {
        buffer.append(Const.getStackTracker(e));
      }

      HopGuiPipelineGraph pipelineGraph =
          (HopGuiPipelineGraph) HopGui.getInstance().getActiveFileTypeHandler();
      if (pipelineGraph != null) {
        if (!pipelineGraph.isExecutionResultsPaneVisible()) {
          pipelineGraph.showExecutionResults();
        }

        pipelineGraph.extraViewTabFolder.setSelection(5);
      }
    } finally {
      previewBusy.set(false);
    }
  }

  protected void asyncUpdatePreview() {

    Runnable update =
        () -> {
          try {
            updatePreview();
          } catch (SWTException e) {
            // Ignore widget disposed errors
          }
        };

    shell.getDisplay().asyncExec(update);
  }

  @Override
  public Shell getShell() {
    return this.shell;
  }

  @Override
  public ICsvInputAwareImportProgressDialog getCsvImportProgressDialog(
      final ICsvInputAwareMeta meta, final int samples, final InputStreamReader reader) {
    return new TextFileCSVImportProgressDialog(
        getShell(), variables, (CsvInputMeta) meta, pipelineMeta, reader, samples, true);
  }

  @Override
  public LogChannel getLogChannel() {
    return log;
  }

  @Override
  public PipelineMeta getPipelineMeta() {
    return pipelineMeta;
  }

  @Override
  public InputStream getInputStream(final ICsvInputAwareMeta meta) {
    InputStream inputStream = null;
    try {
      FileObject fileObject = meta.getHeaderFileObject(variables);
      if (!(fileObject instanceof LocalFile)) {
        // We can only use NIO on local files at the moment, so that's what we limit ourselves to.
        throw new HopException(
            BaseMessages.getString("FileInputDialog.Log.OnlyLocalFilesAreSupported"));
      }

      inputStream = HopVfs.getInputStream(fileObject);
    } catch (final Exception e) {
      logError(BaseMessages.getString("FileInputDialog.ErrorGettingFileDesc.DialogMessage"), e);
    }
    return inputStream;
  }
}
