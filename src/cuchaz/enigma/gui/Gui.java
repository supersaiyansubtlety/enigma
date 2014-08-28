/*******************************************************************************
 * Copyright (c) 2014 Jeff Martin.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Jeff Martin - initial API and implementation
 ******************************************************************************/
package cuchaz.enigma.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import jsyntaxpane.DefaultSyntaxKit;

import com.google.common.collect.Lists;

import cuchaz.enigma.Constants;
import cuchaz.enigma.analysis.BehaviorReferenceTreeNode;
import cuchaz.enigma.analysis.ClassImplementationsTreeNode;
import cuchaz.enigma.analysis.ClassInheritanceTreeNode;
import cuchaz.enigma.analysis.EntryReference;
import cuchaz.enigma.analysis.FieldReferenceTreeNode;
import cuchaz.enigma.analysis.MethodImplementationsTreeNode;
import cuchaz.enigma.analysis.MethodInheritanceTreeNode;
import cuchaz.enigma.analysis.ReferenceTreeNode;
import cuchaz.enigma.analysis.Token;
import cuchaz.enigma.mapping.ArgumentEntry;
import cuchaz.enigma.mapping.ClassEntry;
import cuchaz.enigma.mapping.ConstructorEntry;
import cuchaz.enigma.mapping.Entry;
import cuchaz.enigma.mapping.FieldEntry;
import cuchaz.enigma.mapping.IllegalNameException;
import cuchaz.enigma.mapping.MappingParseException;
import cuchaz.enigma.mapping.MethodEntry;

public class Gui
{
	private static Comparator<String> m_obfClassSorter;
	private static Comparator<String> m_deobfClassSorter;
	
	static
	{
		m_obfClassSorter = new Comparator<String>( )
		{
			@Override
			public int compare( String a, String b )
			{
				if( a.length() != b.length() )
				{
					return a.length() - b.length();
				}
				return a.compareTo( b );
			}
		};
		
		m_deobfClassSorter = new Comparator<String>( )
		{
			@Override
			public int compare( String a, String b )
			{
				// I can never keep this rule straight when writing these damn things...
				// a < b => -1, a == b => 0, a > b => +1
				
				String[] aparts = a.split( "\\." );
				String[] bparts = b.split( "\\." );
				for( int i=0; true; i++ )
				{
					if( i >= aparts.length )
					{
						return -1;
					}
					else if( i >= bparts.length )
					{
						return 1;
					}
					
					int result = aparts[i].compareTo( bparts[i] );
					if( result != 0 )
					{
						return result;
					}
				}
			}
		};
	}
	
	private GuiController m_controller;
	
	// controls
	private JFrame m_frame;
	private JList<String> m_obfClasses;
	private JList<String> m_deobfClasses;
	private JEditorPane m_editor;
	private JPanel m_infoPanel;
	private ObfuscatedHighlightPainter m_obfuscatedHighlightPainter;
	private DeobfuscatedHighlightPainter m_deobfuscatedHighlightPainter;
	private SelectionHighlightPainter m_selectionHighlightPainter;
	private JTree m_inheritanceTree;
	private JTree m_implementationsTree;
	private JTree m_callsTree;
	private JList<Token> m_tokens;
	private JTabbedPane m_tabs;
	
	// dynamic menu items
	private JMenuItem m_closeJarMenu;
	private JMenuItem m_openMappingsMenu;
	private JMenuItem m_saveMappingsMenu;
	private JMenuItem m_saveMappingsAsMenu;
	private JMenuItem m_closeMappingsMenu;
	private JMenuItem m_renameMenu;
	private JMenuItem m_showInheritanceMenu;
	private JMenuItem m_openEntryMenu;
	private JMenuItem m_openPreviousMenu;
	private JMenuItem m_showCallsMenu;
	private JMenuItem m_showImplementationsMenu;
	
	// state
	private EntryReference<Entry,Entry> m_reference;
	private JFileChooser m_jarFileChooser;
	private JFileChooser m_mappingsFileChooser;
	private JFileChooser m_exportFileChooser;
	
	public Gui( )
	{
		// init frame
		m_frame = new JFrame( Constants.Name );
		final Container pane = m_frame.getContentPane();
		pane.setLayout( new BorderLayout() );
		
		// install a global exception handler to the event thread
		CrashDialog.init( m_frame );
		Thread.setDefaultUncaughtExceptionHandler( new UncaughtExceptionHandler( )
		{
			@Override
			public void uncaughtException( Thread thread, Throwable ex )
			{
				CrashDialog.show( ex );
			}
		} );
		
		m_controller = new GuiController( this );
		
		// init file choosers
		m_jarFileChooser = new JFileChooser();
		m_mappingsFileChooser = new JFileChooser();
		m_exportFileChooser = new JFileChooser();
		m_exportFileChooser.setFileSelectionMode( JFileChooser.DIRECTORIES_ONLY );
		
		// init obfuscated classes list
		m_obfClasses = new JList<String>();
		m_obfClasses.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
		m_obfClasses.setLayoutOrientation( JList.VERTICAL );
		m_obfClasses.setCellRenderer( new ClassListCellRenderer() );
		m_obfClasses.addMouseListener( new MouseAdapter()
		{
			@Override
			public void mouseClicked( MouseEvent event )
			{
				if( event.getClickCount() == 2 )
				{
					String selected = m_obfClasses.getSelectedValue();
					if( selected != null )
					{
						if( m_reference != null )
						{
							m_controller.savePreviousReference( m_reference );
						}
						m_controller.openDeclaration( new ClassEntry( selected ) );
					}
				}
			}
		} );
		JScrollPane obfScroller = new JScrollPane( m_obfClasses );
		JPanel obfPanel = new JPanel();
		obfPanel.setLayout( new BorderLayout() );
		obfPanel.add( new JLabel( "Obfuscated Classes" ), BorderLayout.NORTH );
		obfPanel.add( obfScroller, BorderLayout.CENTER );
		
		// init deobfuscated classes list
		m_deobfClasses = new JList<String>();
		m_deobfClasses.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
		m_deobfClasses.setLayoutOrientation( JList.VERTICAL );
		m_deobfClasses.setCellRenderer( new ClassListCellRenderer() );
		m_deobfClasses.addMouseListener( new MouseAdapter()
		{
			@Override
			public void mouseClicked( MouseEvent event )
			{
				if( event.getClickCount() == 2 )
				{
					String selected = m_deobfClasses.getSelectedValue();
					if( selected != null )
					{
						if( m_reference != null )
						{
							m_controller.savePreviousReference( m_reference );
						}
						m_controller.openDeclaration( new ClassEntry( selected ) );
					}
				}
			}
		} );
		JScrollPane deobfScroller = new JScrollPane( m_deobfClasses );
		JPanel deobfPanel = new JPanel();
		deobfPanel.setLayout( new BorderLayout() );
		deobfPanel.add( new JLabel( "De-obfuscated Classes" ), BorderLayout.NORTH );
		deobfPanel.add( deobfScroller, BorderLayout.CENTER );
		
		// init info panel
		m_infoPanel = new JPanel();
		m_infoPanel.setLayout( new GridLayout( 4, 1, 0, 0 ) );
		m_infoPanel.setPreferredSize( new Dimension( 0, 100 ) );
		m_infoPanel.setBorder( BorderFactory.createTitledBorder( "Identifier Info" ) );
		clearReference();
		
		// init editor
		DefaultSyntaxKit.initKit();
		m_obfuscatedHighlightPainter = new ObfuscatedHighlightPainter();
		m_deobfuscatedHighlightPainter = new DeobfuscatedHighlightPainter();
		m_selectionHighlightPainter = new SelectionHighlightPainter();
		m_editor = new JEditorPane();
		m_editor.setEditable( false );
		m_editor.setCaret( new BrowserCaret() );
		JScrollPane sourceScroller = new JScrollPane( m_editor );
		m_editor.setContentType( "text/java" );
		m_editor.addCaretListener( new CaretListener( )
		{
			@Override
			public void caretUpdate( CaretEvent event )
			{
				onCaretMove( event.getDot() );
			}
		} );
		m_editor.addKeyListener( new KeyAdapter( )
		{
			@Override
			public void keyPressed( KeyEvent event )
			{
				switch( event.getKeyCode() )
				{
					case KeyEvent.VK_R:
						startRename();
					break;
					
					case KeyEvent.VK_I:
						showInheritance();
					break;
					
					case KeyEvent.VK_M:
						showImplementations();
					break;
					
					case KeyEvent.VK_N:
						openDeclaration();
					break;
					
					case KeyEvent.VK_P:
						m_controller.openPreviousReference();
					break;
					
					case KeyEvent.VK_C:
						showCalls();
					break;
				}
			}
		} );
		
		// turn off token highlighting (it's wrong most of the time anyway...)
		DefaultSyntaxKit kit = (DefaultSyntaxKit)m_editor.getEditorKit();
		kit.toggleComponent( m_editor, "jsyntaxpane.components.TokenMarker" );
		
		// init editor popup menu
		JPopupMenu popupMenu = new JPopupMenu();
		m_editor.setComponentPopupMenu( popupMenu );
		{
			JMenuItem menu = new JMenuItem( "Rename" );
			menu.addActionListener( new ActionListener( )
			{
				@Override
				public void actionPerformed( ActionEvent event )
				{
					startRename();
				}
			} );
			menu.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_R, 0 ) );
			menu.setEnabled( false );
			popupMenu.add( menu );
			m_renameMenu = menu;
		}
		{
			JMenuItem menu = new JMenuItem( "Show Inheritance" );
			menu.addActionListener( new ActionListener( )
			{
				@Override
				public void actionPerformed( ActionEvent event )
				{
					showInheritance();
				}
			} );
			menu.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_I, 0 ) );
			menu.setEnabled( false );
			popupMenu.add( menu );
			m_showInheritanceMenu = menu;
		}
		{
			JMenuItem menu = new JMenuItem( "Show Implementations" );
			menu.addActionListener( new ActionListener( )
			{
				@Override
				public void actionPerformed( ActionEvent event )
				{
					showImplementations();
				}
			} );
			menu.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_M, 0 ) );
			menu.setEnabled( false );
			popupMenu.add( menu );
			m_showImplementationsMenu = menu;
		}
		{
			JMenuItem menu = new JMenuItem( "Show Calls" );
			menu.addActionListener( new ActionListener( )
			{
				@Override
				public void actionPerformed( ActionEvent event )
				{
					showCalls();
				}
			} );
			menu.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_C, 0 ) );
			menu.setEnabled( false );
			popupMenu.add( menu );
			m_showCallsMenu = menu;
		}
		{
			JMenuItem menu = new JMenuItem( "Go to Declaration" );
			menu.addActionListener( new ActionListener( )
			{
				@Override
				public void actionPerformed( ActionEvent event )
				{
					openDeclaration();
				}
			} );
			menu.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_N, 0 ) );
			menu.setEnabled( false );
			popupMenu.add( menu );
			m_openEntryMenu = menu;
		}
		{
			JMenuItem menu = new JMenuItem( "Go to previous" );
			menu.addActionListener( new ActionListener( )
			{
				@Override
				public void actionPerformed( ActionEvent event )
				{
					m_controller.openPreviousReference();
				}
			} );
			menu.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_P, 0 ) );
			menu.setEnabled( false );
			popupMenu.add( menu );
			m_openPreviousMenu = menu;
		}
		
		// init inheritance panel
		m_inheritanceTree = new JTree();
		m_inheritanceTree.setModel( null );
		m_inheritanceTree.addMouseListener( new MouseAdapter( )
		{
			@Override
			public void mouseClicked( MouseEvent event )
			{
				if( event.getClickCount() == 2 )
				{
					// get the selected node
					TreePath path = m_inheritanceTree.getSelectionPath();
					if( path == null )
					{
						return;
					}
					
					Object node = path.getLastPathComponent();
					if( node instanceof ClassInheritanceTreeNode )
					{
						if( m_reference != null )
						{
							m_controller.savePreviousReference( m_reference );
						}
						m_controller.openDeclaration( new ClassEntry( ((ClassInheritanceTreeNode)node).getObfClassName() ) );
					}
					else if( node instanceof MethodInheritanceTreeNode )
					{
						MethodInheritanceTreeNode methodNode = (MethodInheritanceTreeNode)node;
						if( methodNode.isImplemented() )
						{
							if( m_reference != null )
							{
								m_controller.savePreviousReference( m_reference );
							}
							m_controller.openDeclaration( methodNode.getMethodEntry() );
						}
					}
				}
			}
		} );
		JPanel inheritancePanel = new JPanel();
		inheritancePanel.setLayout( new BorderLayout() );
		inheritancePanel.add( new JScrollPane( m_inheritanceTree ) );
		
		// init implementations panel
		m_implementationsTree = new JTree();
		m_implementationsTree.setModel( null );
		m_implementationsTree.addMouseListener( new MouseAdapter( )
		{
			@Override
			public void mouseClicked( MouseEvent event )
			{
				if( event.getClickCount() == 2 )
				{
					// get the selected node
					TreePath path = m_implementationsTree.getSelectionPath();
					if( path == null )
					{
						return;
					}
					
					Object node = path.getLastPathComponent();
					if( node instanceof ClassImplementationsTreeNode )
					{
						ClassImplementationsTreeNode classNode = (ClassImplementationsTreeNode)node;
						m_controller.openDeclaration( classNode.getClassEntry() );
					}
					else if( node instanceof MethodImplementationsTreeNode )
					{
						MethodImplementationsTreeNode methodNode = (MethodImplementationsTreeNode)node;
						m_controller.openDeclaration( methodNode.getMethodEntry() );
					}
				}
			}
		} );
		JPanel implementationsPanel = new JPanel();
		implementationsPanel.setLayout( new BorderLayout() );
		implementationsPanel.add( new JScrollPane( m_implementationsTree ) );
		
		// init call panel
		m_callsTree = new JTree();
		m_callsTree.setModel( null );
		m_callsTree.addMouseListener( new MouseAdapter( )
		{
			@SuppressWarnings( "unchecked" )
			@Override
			public void mouseClicked( MouseEvent event )
			{
				if( event.getClickCount() == 2 )
				{
					// get the selected node
					TreePath path = m_callsTree.getSelectionPath();
					if( path == null )
					{
						return;
					}
					
					Object node = path.getLastPathComponent();
					if( node instanceof ReferenceTreeNode )
					{
						if( m_reference != null )
						{
							m_controller.savePreviousReference( m_reference );
						}
						ReferenceTreeNode<Entry,Entry> referenceNode = ((ReferenceTreeNode<Entry,Entry>)node);
						if( referenceNode.getReference() != null )
						{
							m_controller.openReference( referenceNode.getReference() );
						}
						else
						{
							m_controller.openDeclaration( referenceNode.getEntry() );
						}
					}
				}
			}
		} );
		m_tokens = new JList<Token>();
		m_tokens.setCellRenderer( new TokenListCellRenderer( m_controller ) );
		m_tokens.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
		m_tokens.setLayoutOrientation( JList.VERTICAL );
		m_tokens.addMouseListener( new MouseAdapter()
		{
			@Override
			public void mouseClicked( MouseEvent event )
			{
				if( event.getClickCount() == 2 )
				{
					Token selected = m_tokens.getSelectedValue();
					if( selected != null )
					{
						showToken( selected );
					}
				}
			}
		} );
		m_tokens.setPreferredSize( new Dimension( 0, 200 ) );
		m_tokens.setMinimumSize( new Dimension( 0, 200 ) );
		JSplitPane callPanel = new JSplitPane( JSplitPane.VERTICAL_SPLIT, true, new JScrollPane( m_callsTree ), new JScrollPane( m_tokens ) );
		callPanel.setResizeWeight( 1 ); // let the top side take all the slack
		callPanel.resetToPreferredSizes();
		
		// layout controls
		JSplitPane splitLeft = new JSplitPane( JSplitPane.VERTICAL_SPLIT, true, obfPanel, deobfPanel );
		splitLeft.setPreferredSize( new Dimension( 250, 0 ) );
		JPanel centerPanel = new JPanel();
		centerPanel.setLayout( new BorderLayout() );
		centerPanel.add( m_infoPanel, BorderLayout.NORTH );
		centerPanel.add( sourceScroller, BorderLayout.CENTER );
		m_tabs = new JTabbedPane();
		m_tabs.setPreferredSize( new Dimension( 250, 0 ) );
		m_tabs.addTab( "Inheritance", inheritancePanel );
		m_tabs.addTab( "Implementations", implementationsPanel );
		m_tabs.addTab( "Call Graph", callPanel );
		JSplitPane splitRight = new JSplitPane( JSplitPane.HORIZONTAL_SPLIT, true, centerPanel, m_tabs );
		splitRight.setResizeWeight( 1 ); // let the left side take all the slack
		splitRight.resetToPreferredSizes();
		JSplitPane splitCenter = new JSplitPane( JSplitPane.HORIZONTAL_SPLIT, true, splitLeft, splitRight );
		splitCenter.setResizeWeight( 0 ); // let the right side take all the slack
		pane.add( splitCenter, BorderLayout.CENTER );
		
		// init menus
		JMenuBar menuBar = new JMenuBar();
		m_frame.setJMenuBar( menuBar );
		{
			JMenu menu = new JMenu( "File" );
			menuBar.add( menu );
			{
				JMenuItem item = new JMenuItem( "Open Jar..." );
				menu.add( item );
				item.addActionListener( new ActionListener( )
				{
					@Override
					public void actionPerformed( ActionEvent event )
					{
						if( m_jarFileChooser.showOpenDialog( m_frame ) == JFileChooser.APPROVE_OPTION )
						{
							try
							{
								m_controller.openJar( m_jarFileChooser.getSelectedFile() );
							}
							catch( IOException ex )
							{
								throw new Error( ex );
							}
						}
					}
				} );
			}
			{
				JMenuItem item = new JMenuItem( "Close Jar" );
				menu.add( item );
				item.addActionListener( new ActionListener( )
				{
					@Override
					public void actionPerformed( ActionEvent event )
					{
						m_controller.closeJar();
					}
				} );
				m_closeJarMenu = item;
			}
			menu.addSeparator();
			{
				JMenuItem item = new JMenuItem( "Open Mappings..." );
				menu.add( item );
				item.addActionListener( new ActionListener( )
				{
					@Override
					public void actionPerformed( ActionEvent event )
					{
						if( m_mappingsFileChooser.showOpenDialog( m_frame ) == JFileChooser.APPROVE_OPTION )
						{
							try
							{
								m_controller.openMappings( m_mappingsFileChooser.getSelectedFile() );
							}
							catch( IOException ex )
							{
								throw new Error( ex );
							}
							catch( MappingParseException ex )
							{
								JOptionPane.showMessageDialog( m_frame, ex.getMessage() );
							}
						}
					}
				} );
				m_openMappingsMenu = item;
			}
			{
				JMenuItem item = new JMenuItem( "Save Mappings" );
				menu.add( item );
				item.addActionListener( new ActionListener( )
				{
					@Override
					public void actionPerformed( ActionEvent event )
					{
						try
						{
							m_controller.saveMappings( m_mappingsFileChooser.getSelectedFile() );
						}
						catch( IOException ex )
						{
							throw new Error( ex );
						}
					}
				} );
				item.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK ) );
				m_saveMappingsMenu = item;
			}
			{
				JMenuItem item = new JMenuItem( "Save Mappings As..." );
				menu.add( item );
				item.addActionListener( new ActionListener( )
				{
					@Override
					public void actionPerformed( ActionEvent event )
					{
						if( m_mappingsFileChooser.showSaveDialog( m_frame ) == JFileChooser.APPROVE_OPTION )
						{
							try
							{
								m_controller.saveMappings( m_mappingsFileChooser.getSelectedFile() );
								m_saveMappingsMenu.setEnabled( true );
							}
							catch( IOException ex )
							{
								throw new Error( ex );
							}
						}
					}
				} );
				item.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK ) );
				m_saveMappingsAsMenu = item;
			}
			{
				JMenuItem item = new JMenuItem( "Close Mappings" );
				menu.add( item );
				item.addActionListener( new ActionListener( )
				{
					@Override
					public void actionPerformed( ActionEvent event )
					{
						m_controller.closeMappings();
					}
				} );
				m_closeMappingsMenu = item;
			}
			menu.addSeparator();
			{
				JMenuItem item = new JMenuItem( "Export..." );
				menu.add( item );
				item.addActionListener( new ActionListener( ) 
				{
					@Override
					public void actionPerformed( ActionEvent event )
					{
						if( m_exportFileChooser.showSaveDialog( m_frame ) == JFileChooser.APPROVE_OPTION )
						{
							m_controller.export( m_exportFileChooser.getSelectedFile() );
						}
					}
				} );
			}
			menu.addSeparator();
			{
				JMenuItem item = new JMenuItem( "Exit" );
				menu.add( item );
				item.addActionListener( new ActionListener( )
				{
					@Override
					public void actionPerformed( ActionEvent event )
					{
						close();
					}
				} );
			}
		}
		{
			JMenu menu = new JMenu( "Help" );
			menuBar.add( menu );
			{
				JMenuItem item = new JMenuItem( "About" );
				menu.add( item );
				item.addActionListener( new ActionListener( )
				{
					@Override
					public void actionPerformed( ActionEvent event )
					{
						AboutDialog.show( m_frame );
					}
				} );
			}
		}
		
		// init state
		onCloseJar();
		
		m_frame.addWindowListener( new WindowAdapter( )
		{
			@Override
			public void windowClosing( WindowEvent event )
			{
				close();
			}
		} );
		
		// show the frame
		pane.doLayout();
		m_frame.setSize( 1024, 576 );
		m_frame.setMinimumSize( new Dimension( 640, 480 ) );
		m_frame.setVisible( true );
		m_frame.setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );
	}
	
	public JFrame getFrame( )
	{
		return m_frame;
	}
	
	public GuiController getController( )
	{
		return m_controller;
	}
	
	public void onOpenJar( String jarName )
	{
		// update gui
		m_frame.setTitle( Constants.Name + " - " + jarName );
		setSource( null );
		
		// update menu
		m_closeJarMenu.setEnabled( true );
		m_openMappingsMenu.setEnabled( true );
		m_saveMappingsMenu.setEnabled( false );
		m_saveMappingsAsMenu.setEnabled( true );
		m_closeMappingsMenu.setEnabled( true );
	}
	
	public void onCloseJar( )
	{
		// update gui
		m_frame.setTitle( Constants.Name );
		setObfClasses( null );
		setDeobfClasses( null );
		setSource( null );
		
		// update menu
		m_closeJarMenu.setEnabled( false );
		m_openMappingsMenu.setEnabled( false );
		m_saveMappingsMenu.setEnabled( false );
		m_saveMappingsAsMenu.setEnabled( false );
		m_closeMappingsMenu.setEnabled( false );
	}
	
	public void setObfClasses( List<String> obfClasses )
	{
		if( obfClasses != null )
		{
			Vector<String> sortedClasses = new Vector<String>( obfClasses );
			Collections.sort( sortedClasses, m_obfClassSorter );
			m_obfClasses.setListData( sortedClasses );
		}
		else
		{
			m_obfClasses.setListData( new Vector<String>() );
		}
	}
	
	public void setDeobfClasses( List<String> deobfClasses )
	{
		if( deobfClasses != null )
		{
			Vector<String> sortedClasses = new Vector<String>( deobfClasses );
			Collections.sort( sortedClasses, m_deobfClassSorter );
			m_deobfClasses.setListData( sortedClasses );
		}
		else
		{
			m_deobfClasses.setListData( new Vector<String>() );
		}
	}
	
	public void setMappingsFile( File file )
	{
		m_mappingsFileChooser.setSelectedFile( file );
		m_saveMappingsMenu.setEnabled( file != null );
	}
	
	public void setSource( String source )
	{
		m_editor.getHighlighter().removeAllHighlights();
		m_editor.setText( source );
	}
	
	public void showToken( final Token token )
	{
		if( token == null )
		{
			throw new IllegalArgumentException( "Token cannot be null!" );
		}
		
		// set the caret position to the token
		m_editor.setCaretPosition( token.start );
		m_editor.grabFocus();
		
		try
		{
			// make sure the token is visible in the scroll window
			Rectangle start = m_editor.modelToView( token.start );
			Rectangle end = m_editor.modelToView( token.end );
			final Rectangle show = start.union( end );
			show.grow( start.width*10, start.height*6 );
			SwingUtilities.invokeLater( new Runnable( )
			{
				@Override
				public void run( )
				{
					m_editor.scrollRectToVisible( show );
				}
			} );
		}
		catch( BadLocationException ex )
		{
			throw new Error( ex );
		}
		
		// highlight the token momentarily
		final Timer timer = new Timer( 200, new ActionListener( )
		{
			private int m_counter = 0;
			private Object m_highlight = null;
			
			@Override
			public void actionPerformed( ActionEvent event )
			{
				if( m_counter % 2 == 0 )
				{
					try
					{
						m_highlight = m_editor.getHighlighter().addHighlight( token.start, token.end, m_selectionHighlightPainter );
					}
					catch( BadLocationException ex )
					{
						// don't care
					}
				}
				else if( m_highlight != null )
				{
					m_editor.getHighlighter().removeHighlight( m_highlight );
				}
				
				if( m_counter++ > 6 )
				{
					Timer timer = (Timer)event.getSource();
					timer.stop();
				}
			}
		} );
		timer.start();
		
		redraw();
	}
	
	public void showTokens( Collection<Token> tokens )
	{
		Vector<Token> sortedTokens = new Vector<Token>( tokens );
		Collections.sort( sortedTokens );
		if( sortedTokens.size() > 1 )
		{
			// sort the tokens and update the tokens panel
			m_tokens.setListData( sortedTokens );
			m_tokens.setSelectedIndex( 0 );
		}
		else
		{
			m_tokens.setListData( new Vector<Token>() );
		}
		
		// show the first token
		showToken( sortedTokens.get( 0 ) );
	}
	
	public void setHighlightedTokens( Iterable<Token> obfuscatedTokens, Iterable<Token> deobfuscatedTokens )
	{
		// remove any old highlighters
		m_editor.getHighlighter().removeAllHighlights();
		
		
		// color things based on the index
		if( obfuscatedTokens != null )
		{
			setHighlightedTokens( obfuscatedTokens, m_obfuscatedHighlightPainter );
		}
		if( deobfuscatedTokens != null )
		{
			setHighlightedTokens( deobfuscatedTokens, m_deobfuscatedHighlightPainter );
		}
		
		redraw();
	}
	
	private void setHighlightedTokens( Iterable<Token> tokens, Highlighter.HighlightPainter painter )
	{
		for( Token token : tokens )
		{
			try
			{
				m_editor.getHighlighter().addHighlight( token.start, token.end, painter );
			}
			catch( BadLocationException ex )
			{
				throw new IllegalArgumentException( ex );
			}
		}
	}
	
	private void clearReference( )
	{
		m_infoPanel.removeAll();
		JLabel label = new JLabel( "No identifier selected" );
		GuiTricks.unboldLabel( label );
		label.setHorizontalAlignment( JLabel.CENTER );
		m_infoPanel.add( label );
		
		redraw();
	}
	
	private void showReference( EntryReference<Entry,Entry> reference )
	{
		if( reference == null )
		{
			clearReference();
			return;
		}
		
		m_reference = reference;
		
		m_infoPanel.removeAll();
		if( reference.entry instanceof ClassEntry )
		{
			showClassEntry( (ClassEntry)m_reference.entry );
		}
		else if( m_reference.entry instanceof FieldEntry )
		{
			showFieldEntry( (FieldEntry)m_reference.entry );
		}
		else if( m_reference.entry instanceof MethodEntry )
		{
			showMethodEntry( (MethodEntry)m_reference.entry );
		}
		else if( m_reference.entry instanceof ConstructorEntry )
		{
			showConstructorEntry( (ConstructorEntry)m_reference.entry );
		}
		else if( m_reference.entry instanceof ArgumentEntry )
		{
			showArgumentEntry( (ArgumentEntry)m_reference.entry );
		}
		else
		{
			throw new Error( "Unknown entry type: " + m_reference.entry.getClass().getName() );
		}
		
		redraw();
	}
	
	private void showClassEntry( ClassEntry entry )
	{
		addNameValue( m_infoPanel, "Class", entry.getName() );
	}
	
	private void showFieldEntry( FieldEntry entry )
	{
		addNameValue( m_infoPanel, "Field", entry.getName() );
		addNameValue( m_infoPanel, "Class", entry.getClassEntry().getName() );
	}
	
	private void showMethodEntry( MethodEntry entry )
	{
		addNameValue( m_infoPanel, "Method", entry.getName() );
		addNameValue( m_infoPanel, "Class", entry.getClassEntry().getName() );
		addNameValue( m_infoPanel, "Signature", entry.getSignature() );
	}
	
	private void showConstructorEntry( ConstructorEntry entry )
	{
		addNameValue( m_infoPanel, "Constructor", entry.getClassEntry().getName() );
		addNameValue( m_infoPanel, "Signature", entry.getSignature() );
	}
	
	private void showArgumentEntry( ArgumentEntry entry )
	{
		addNameValue( m_infoPanel, "Argument", entry.getName() );
		addNameValue( m_infoPanel, "Class", entry.getClassEntry().getName() );
		addNameValue( m_infoPanel, "Method", entry.getMethodEntry().getName() );
		addNameValue( m_infoPanel, "Index", Integer.toString( entry.getIndex() ) );
	}
	
	private void addNameValue( JPanel container, String name, String value )
	{
		JPanel panel = new JPanel();
		panel.setLayout( new FlowLayout( FlowLayout.LEFT, 6, 0 ) );
		container.add( panel );
		
		JLabel label = new JLabel( name + ":", JLabel.RIGHT );
		label.setPreferredSize( new Dimension( 100, label.getPreferredSize().height ) );
		panel.add( label );
		
		panel.add( GuiTricks.unboldLabel( new JLabel( value, JLabel.LEFT ) ) );
	}
	
	private void onCaretMove( int pos )
	{
		Token token = m_controller.getToken( pos );
		boolean isToken = token != null;
		
		m_reference = m_controller.getDeobfReference( token );
		boolean isClassEntry = isToken && m_reference.entry instanceof ClassEntry;
		boolean isFieldEntry = isToken && m_reference.entry instanceof FieldEntry;
		boolean isMethodEntry = isToken && m_reference.entry instanceof MethodEntry;
		boolean isConstructorEntry = isToken && m_reference.entry instanceof ConstructorEntry;
		
		if( isToken )
		{
			showReference( m_reference );
		}
		else
		{
			clearReference();
		}
		
		m_renameMenu.setEnabled( isToken );
		m_showInheritanceMenu.setEnabled( isClassEntry || isMethodEntry || isConstructorEntry );
		m_showImplementationsMenu.setEnabled( isClassEntry || isMethodEntry );
		m_showCallsMenu.setEnabled( isFieldEntry || isMethodEntry || isConstructorEntry );
		m_openEntryMenu.setEnabled( isClassEntry || isFieldEntry || isMethodEntry || isConstructorEntry );
		m_openPreviousMenu.setEnabled( m_controller.hasPreviousLocation() );
	}
	
	private void startRename( )
	{
		// init the text box
		final JTextField text = new JTextField();
		text.setText( m_reference.entry.getName() );
		text.setPreferredSize( new Dimension( 360, text.getPreferredSize().height ) );
		text.addKeyListener( new KeyAdapter( )
		{
			@Override
			public void keyPressed( KeyEvent event )
			{
				switch( event.getKeyCode() )
				{
					case KeyEvent.VK_ENTER:
						finishRename( text, true );
					break;
					
					case KeyEvent.VK_ESCAPE:
						finishRename( text, false );
					break;
				}
			}
		} );
		
		// find the label with the name and replace it with the text box
		JPanel panel = (JPanel)m_infoPanel.getComponent( 0 );
		panel.remove( panel.getComponentCount() - 1 );
		panel.add( text );
		text.grabFocus();
		text.selectAll();
		
		redraw();
	}
	
	private void finishRename( JTextField text, boolean saveName )
	{
		String newName = text.getText();
		if( saveName && newName != null && newName.length() > 0 )
		{
			try
			{
				m_controller.rename( m_reference, newName );
			}
			catch( IllegalNameException ex )
			{
				text.setBorder( BorderFactory.createLineBorder( Color.red, 1 ) );
				text.setToolTipText( ex.getReason() );
				GuiTricks.showToolTipNow( text );
			}
			return;
		}
		
		// abort the rename
		JPanel panel = (JPanel)m_infoPanel.getComponent( 0 );
		panel.remove( panel.getComponentCount() - 1 );
		panel.add( GuiTricks.unboldLabel( new JLabel( m_reference.entry.getName(), JLabel.LEFT ) ) );
		
		m_editor.grabFocus();
		
		redraw();
	}
	
	private void showInheritance( )
	{
		if( m_reference == null )
		{
			return;
		}
		
		m_inheritanceTree.setModel( null );
		
		if( m_reference.entry instanceof ClassEntry )
		{
			// get the class inheritance
			ClassInheritanceTreeNode classNode = m_controller.getClassInheritance( (ClassEntry)m_reference.entry );
			
			// show the tree at the root
			TreePath path = getPathToRoot( classNode );
			m_inheritanceTree.setModel( new DefaultTreeModel( (TreeNode)path.getPathComponent( 0 ) ) );
			m_inheritanceTree.expandPath( path );
			m_inheritanceTree.setSelectionRow( m_inheritanceTree.getRowForPath( path ) );
		}
		else if( m_reference.entry instanceof MethodEntry )
		{
			// get the method inheritance
			MethodInheritanceTreeNode classNode = m_controller.getMethodInheritance( (MethodEntry)m_reference.entry );
			
			// show the tree at the root
			TreePath path = getPathToRoot( classNode );
			m_inheritanceTree.setModel( new DefaultTreeModel( (TreeNode)path.getPathComponent( 0 ) ) );
			m_inheritanceTree.expandPath( path );
			m_inheritanceTree.setSelectionRow( m_inheritanceTree.getRowForPath( path ) );
		}
		
		m_tabs.setSelectedIndex( 0 );
		redraw();
	}
	
	private void showImplementations( )
	{
		if( m_reference == null )
		{
			return;
		}
		
		m_implementationsTree.setModel( null );
		
		if( m_reference.entry instanceof ClassEntry )
		{
			// get the class implementations
			ClassImplementationsTreeNode node = m_controller.getClassImplementations( (ClassEntry)m_reference.entry );
			if( node != null )
			{
				// show the tree at the root
				TreePath path = getPathToRoot( node );
				m_implementationsTree.setModel( new DefaultTreeModel( (TreeNode)path.getPathComponent( 0 ) ) );
				m_implementationsTree.expandPath( path );
				m_implementationsTree.setSelectionRow( m_implementationsTree.getRowForPath( path ) );
			}
		}
		else if( m_reference.entry instanceof MethodEntry )
		{
			// get the method implementations
			MethodImplementationsTreeNode node = m_controller.getMethodImplementations( (MethodEntry)m_reference.entry );
			if( node != null )
			{
				// show the tree at the root
				TreePath path = getPathToRoot( node );
				m_implementationsTree.setModel( new DefaultTreeModel( (TreeNode)path.getPathComponent( 0 ) ) );
				m_implementationsTree.expandPath( path );
				m_implementationsTree.setSelectionRow( m_implementationsTree.getRowForPath( path ) );
			}
		}
		
		m_tabs.setSelectedIndex( 1 );
		redraw();
	}
	
	private void showCalls( )
	{
		if( m_reference == null )
		{
			return;
		}
		
		if( m_reference.entry instanceof FieldEntry )
		{
			FieldReferenceTreeNode node = m_controller.getFieldReferences( (FieldEntry)m_reference.entry );
			m_callsTree.setModel( new DefaultTreeModel( node ) );
		}
		else if( m_reference.entry instanceof MethodEntry )
		{
			BehaviorReferenceTreeNode node = m_controller.getMethodReferences( (MethodEntry)m_reference.entry );
			m_callsTree.setModel( new DefaultTreeModel( node ) );
		}
		else if( m_reference.entry instanceof ConstructorEntry )
		{
			BehaviorReferenceTreeNode node = m_controller.getMethodReferences( (ConstructorEntry)m_reference.entry );
			m_callsTree.setModel( new DefaultTreeModel( node ) );
		}
		
		m_tabs.setSelectedIndex( 2 );
		redraw();
	}
	
	private TreePath getPathToRoot( TreeNode node )
	{
		List<TreeNode> nodes = Lists.newArrayList();
		TreeNode n = node;
		do
		{
			nodes.add( n );
			n = n.getParent();
		}
		while( n != null );
		Collections.reverse( nodes );
		return new TreePath( nodes.toArray() );
	}
	
	private void openDeclaration( )
	{	
		if( m_reference == null )
		{
			return;
		}
		m_controller.savePreviousReference( m_reference );
		m_controller.openDeclaration( m_reference.entry );
	}
	
	private void close( )
	{
		if( !m_controller.isDirty() )
		{
			// everything is saved, we can exit safely
			m_frame.dispose();
		}
		else
		{
			// ask to save before closing
			String[] options = {
				"Save and exit",
				"Discard changes",
				"Cancel"
			};
			int response = JOptionPane.showOptionDialog(
				m_frame,
				"Your mappings have not been saved yet. Do you want to save?",
				"Save your changes?",
				JOptionPane.YES_NO_CANCEL_OPTION,
				JOptionPane.QUESTION_MESSAGE,
				null,
				options,
				options[2]
			);
			switch( response )
			{
				case JOptionPane.YES_OPTION: // save and exit
					if( m_mappingsFileChooser.getSelectedFile() != null || m_mappingsFileChooser.showSaveDialog( m_frame ) == JFileChooser.APPROVE_OPTION )
					{
						try
						{
							m_controller.saveMappings( m_mappingsFileChooser.getSelectedFile() );
							m_frame.dispose();
						}
						catch( IOException ex )
						{
							throw new Error( ex );
						}
					}
				break;
				
				case JOptionPane.NO_OPTION:
					// don't save, exit
					m_frame.dispose();
				break;
				
				// cancel means do nothing
			}
		}
	}

	private void redraw( )
	{
		m_frame.validate();
		m_frame.repaint();
	}
}
