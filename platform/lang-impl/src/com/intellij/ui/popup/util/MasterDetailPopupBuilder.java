/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.popup.util;

import com.intellij.ide.bookmarks.Bookmark;
import com.intellij.ide.bookmarks.BookmarkItem;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.Gray;
import com.intellij.ui.components.JBList;
import com.intellij.ui.speedSearch.FilteringListModel;
import com.intellij.util.Alarm;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * User: zajac
 * Date: 5/6/12
 * Time: 2:06 PM
 * To change this template use File | Settings | File Templates.
 */
public class MasterDetailPopupBuilder {

  private static final Color BORDER_COLOR = Gray._135;
  private Project myProject;
  private ActionGroup myActions;
  private JBList myList;
  private Delegate myDelegate;

  public MasterDetailPopupBuilder(Project project) {

    myProject = project;
  }


  public JBPopup createMasterDetailPopup() {
    final JLabel pathLabel = new JLabel(" ");
    pathLabel.setHorizontalAlignment(SwingConstants.RIGHT);

    final Font font = pathLabel.getFont();
    pathLabel.setFont(font.deriveFont((float)10));

    final Alarm updateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    final DetailViewImpl detailView = new DetailViewImpl(myProject);

    myList.setCellRenderer(new ItemRenderer(myProject));

    myList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      private String getTitle2Text(String fullText) {
        int labelWidth = pathLabel.getWidth();
        if (fullText == null || fullText.length() == 0) return " ";
        while (pathLabel.getFontMetrics(pathLabel.getFont()).stringWidth(fullText) > labelWidth) {
          int sep = fullText.indexOf(File.separatorChar, 4);
          if (sep < 0) return fullText;
          fullText = "..." + fullText.substring(sep);
        }

        return fullText;
      }

      public void valueChanged(final ListSelectionEvent e) {
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            updatePathLabel();
          }
        });
      }

      private void updatePreviewPanel(final ItemWrapper wrapper) {
        updateAlarm.cancelAllRequests();
        updateAlarm.addRequest(new Runnable() {
          public void run() {
            detailView.updateWithItem(wrapper);
          }
        }, 300);
      }

      private void updatePathLabel() {
        final Object[] values = myList.getSelectedValues();
        ItemWrapper wrapper = null;
        if (values != null && values.length == 1) {
          wrapper = (ItemWrapper)values[0];
          pathLabel.setText(getTitle2Text(wrapper.footerText()));
        }
        else {
          pathLabel.setText(" ");
        }
        updatePreviewPanel(wrapper);
      }
    });

    Runnable runnable = new Runnable() {
      public void run() {
        IdeFocusManager.getInstance(myProject).doWhenFocusSettlesDown(new Runnable() {
          public void run() {
            Object[] values = myList.getSelectedValues();

            if (values.length == 1) {
              ((ItemWrapper)values[0]).execute(myProject);
            }
            else {
              for (Object value : values) {
                if (value instanceof BookmarkItem) {
                  ((ItemWrapper)value).execute(myProject);
                }
              }
            }
          }
        });
      }
    };

    if (myList.getModel().getSize() == 0) {
      myList.clearSelection();
    }

    JPanel footerPanel = new JPanel(new BorderLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(BORDER_COLOR);
        g.drawLine(0, 0, getWidth(), 0);
      }
    };

    footerPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    footerPanel.add(pathLabel);

    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("", myActions, true);
    actionToolbar.setReservePlaceAutoPopupIcon(false);
    actionToolbar.setMinimumButtonSize(new Dimension(20, 20));
    final JComponent toolBar = actionToolbar.getComponent();
    toolBar.setOpaque(false);

    final JBPopup popup = new PopupChooserBuilder(myList).
      setTitle(myDelegate.getTitle()).
      setMovable(true).
      setResizable(true).
      setAutoselectOnMouseMove(false).
      setSettingButton(toolBar).
      setSouthComponent(footerPanel).
      setEastComponent(detailView).
      setItemChoosenCallback(runnable).
      setMayBeParent(true).
      setFilteringEnabled(new Function<Object, String>() {
        public String fun(Object o) {
          return ((ItemWrapper)o).speedSearchText();
        }
      }).createPopup();

    myList.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_DELETE) {
          int index = myList.getSelectedIndex();
          if (index == -1 || index >= myList.getModel().getSize()) {
            return;
          }
          Object[] values = myList.getSelectedValues();
          for (Object value : values) {
            ItemWrapper item = (ItemWrapper)value;

            DefaultListModel model = myList.getModel() instanceof DefaultListModel
                                     ? (DefaultListModel)myList.getModel()
                                     : (DefaultListModel)((FilteringListModel)myList.getModel()).getOriginalModel();
            if (item.allowedToRemove()) {
              model.removeElement(item);

              if (model.getSize() > 0) {
                if (model.getSize() == index) {
                  myList.setSelectedIndex(model.getSize() - 1);
                }
                else if (model.getSize() > index) {
                  myList.setSelectedIndex(index);
                }
              }
              else {
                myList.clearSelection();
              }
              item.removed(myProject);
            }

          }
        }
        else if (e.getModifiersEx() == 0) {
          myDelegate.handleMnemonic(e, myProject, popup);
        }
      }
    });
    return popup;
  }

  public MasterDetailPopupBuilder setActionsGroup(ActionGroup actions) {
    myActions = actions;
    return this;
  }

  public MasterDetailPopupBuilder setList(JBList list) {
    myList = list;
    return this;
  }

  public MasterDetailPopupBuilder setDelegate(Delegate delegate) {
    myDelegate = delegate;
    return this;
  }

  public interface Delegate {
    String getTitle();

    void handleMnemonic(KeyEvent e, Project project, JBPopup popup);

    void itemRemoved(ItemWrapper item, Project project);

    boolean hasItemsWithMnemonic(Project project);
  }

  public class ItemRenderer extends JPanel implements ListCellRenderer {
    private final Project myProject;
    private final ColoredListCellRenderer myRenderer;

    private ItemRenderer(Project project) {
      super(new BorderLayout());
      myProject = project;

      setBackground(UIUtil.getListBackground());

      final JLabel mnemonicLabel = new JLabel();
      mnemonicLabel.setFont(Bookmark.MNEMONIC_FONT);

      mnemonicLabel.setPreferredSize(new JLabel("W.").getPreferredSize());
      mnemonicLabel.setOpaque(false);

      final boolean hasMnemonics = myDelegate.hasItemsWithMnemonic(project);
      if (hasMnemonics) {
        add(mnemonicLabel, BorderLayout.WEST);
      }

      myRenderer = new ColoredListCellRenderer() {
        @Override
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          if (value instanceof ItemWrapper) {
            final ItemWrapper wrapper = (ItemWrapper)value;
            wrapper.setupRenderer(this, myProject, selected);
            if (hasMnemonics) {
              wrapper.updateMnemonicLabel(mnemonicLabel);
            }
          }
        }
      };
      add(myRenderer, BorderLayout.CENTER);
    }

    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      myRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      return this;
    }
  }

}
