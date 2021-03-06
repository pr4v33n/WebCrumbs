package edu.stanford.webcrumbs.visualization;

/*
 * Starts the visualization of the graph
 * 
 * Author : Subodh Iyengar
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;


import edu.stanford.webcrumbs.Arguments;
import edu.stanford.webcrumbs.data.StringMatch;
import edu.stanford.webcrumbs.graph.search.Indexer;
import edu.stanford.webcrumbs.graph.search.PrefuseIndexer;
import edu.stanford.webcrumbs.ranker.NodeRanker;
import prefuse.Constants;
import prefuse.Display;
import prefuse.Visualization;
import prefuse.action.ActionList;
import prefuse.action.RepaintAction;
import prefuse.action.animate.ColorAnimator;
import prefuse.action.animate.QualityControlAnimator;
import prefuse.action.assignment.ColorAction;
import prefuse.action.assignment.DataColorAction;
import prefuse.action.assignment.FontAction;
import prefuse.action.layout.Layout;
import prefuse.activity.SlowInSlowOutPacer;
import prefuse.controls.FocusControl;
import prefuse.controls.PanControl;
import prefuse.controls.SubtreeDragControl;
import prefuse.controls.ZoomControl;
import prefuse.data.Edge;
import prefuse.data.Graph;
import prefuse.data.Node;
import prefuse.data.Tuple;
import prefuse.data.expression.Predicate;
import prefuse.render.AbstractShapeRenderer;
import prefuse.render.DefaultRendererFactory;
import prefuse.render.EdgeRenderer;
import prefuse.render.LabelRenderer;
import prefuse.util.ColorLib;
import prefuse.util.FontLib;
import prefuse.util.ui.UILib;
import prefuse.visual.VisualItem;
import prefuse.visual.expression.InGroupPredicate;


public class PrefuseVis implements edu.stanford.webcrumbs.visualization.Visualization{
	Graph graph;
	String graphGroup = "graph";
	String graphNodes = "graph.nodes";
	String graphEdges = "graph.edges";
	Visualization vis;
	JFrame prefuseFrame;
	
	JPanel resultPanel;
	final int FOUND_COLOR = ColorLib.rgb(200,0,0);
	final int REDIRECT_COLOR = ColorLib.rgb(0,240,0);
	
	final int TIP_WIDTH = 500;
	final int TIP_HEIGHT = 500;
	final int NUM_DISPLAY = 20;
	//to make more efficient
	RepaintAction repaint;
	
	Indexer index;
	
	ArrayList<ItemState> previousState = 
		new ArrayList<PrefuseVis.ItemState>();
	
	NodeRanker<prefuse.data.Tuple> ranker;
	
	NodeElementColorAction nodeColorAction;
	ElementColorAction edgeColorAction;
	String labelField; 
	String nodeColorField;
	String edgeColorField;
	
	class RoundLabelRenderer extends LabelRenderer{
		Ellipse2D.Double round = new Ellipse2D.Double();
		
		RoundLabelRenderer(String labelField){
			super(labelField);
		}
		
		@Override
		public java.awt.Shape getRawShape(VisualItem item){
			java.awt.Shape shape = super.getRawShape(item);
			Rectangle bounds = shape.getBounds();
			round.x = bounds.x;
			round.y = bounds.y;

			round.width = 20;
			round.height = 20;
			if (ranker != null){
				Double dim = ranker.getSize((Tuple)graph.getNode(item.getInt("key")));
				if (dim != null){
					round.width *= dim;
					round.height *= dim;
				}
			}
			
			return round;
		}
	}
	class NormalLabelRenderer extends LabelRenderer{
		
		//RoundRectangle2D.Double round = new RoundRectangle2D.Double();
		Ellipse2D.Double round = new Ellipse2D.Double();
		
		
		public int getHorizontalTextAlignment(){
			return Constants.CENTER;
		}
		
		NormalLabelRenderer(String labelField){
			super(labelField);
		}
		
		@Override
		public java.awt.Shape getRawShape(VisualItem item){
			java.awt.Shape shape = super.getRawShape(item);
			Rectangle bounds = shape.getBounds();
			round.x = (bounds.x);
			round.y = (bounds.y);
			round.width = 20;
			round.height = 20;
		
			if (ranker != null){
				Tuple itemTuple = item.getSourceTuple();
				Double dim = ranker.getSize(itemTuple);
				if (dim != null){
					round.width = dim;
					round.height = dim;
				}
			}
			return round;
		}
	}
	
	class SearchColorAction extends DataColorAction{
		String field;
		String[] mapping;
		int[] palette;
		String group;
		
		public SearchColorAction(String group, String field, 
				int type, String colorField, 
				int[] palette, String[] mapping){
			super(group, field, type, colorField, palette);
			this.field = field;
			this.palette = palette;
			this.mapping = mapping;
			this.group = group;
		}
		
		@Override
		public int getColor(VisualItem item){
			Tuple sourceTuple = item.getSourceTuple();
			VisualItem visualItem = 
				vis.getVisualItem(group, sourceTuple);
			String fieldName = this.getDataField();
			String fieldVal = sourceTuple.getString(fieldName);
			
			if (fieldVal.equals("true")){
				return palette[0];
			}else if (group.equals(graphNodes)){
				return visualItem.getFillColor();
			}else if (group.equals(graphEdges)){
				return visualItem.getStrokeColor();
			}
			return 0;
		}
	}

	class NodeElementColorAction extends DataColorAction{
		String field;
		String[] mapping;
		int[] palette;
		
		public NodeElementColorAction(String group, String field, 
				int type, String colorField, 
				int[] palette, String[] mapping){
			super(group, field, type, colorField, palette);
			this.field = field;
			this.palette = palette;
			this.mapping = mapping;
		}
		
		@Override
		public int getColor(VisualItem item){
			Tuple sourceTuple = item.getSourceTuple();
			String fieldName = this.getDataField();
			
			String fieldVal = sourceTuple.getString(fieldName);
			
			if (ranker != null){
				Integer color = ranker.getColor(sourceTuple);
				if (color != null){
					return color;
				}
			}
			
			for (int i = 0; i < mapping.length; i++){
				String value = mapping[i];
				if (value.equals(fieldVal)){
					return palette[i];
				}
			}
			return 0;
		}
	}
	
	class ElementColorAction extends DataColorAction{
		String field;
		String[] mapping;
		int[] palette;
		
		public ElementColorAction(String group, String field, 
				int type, String colorField, 
				int[] palette, String[] mapping){
			super(group, field, type, colorField, palette);
			this.field = field;
			this.palette = palette;
			this.mapping = mapping;
		}
		
		@Override
		public int getColor(VisualItem item){
			Tuple sourceTuple = item.getSourceTuple();
			String fieldName = this.getDataField();
			
			String fieldVal = sourceTuple.getString(fieldName);
			
			for (int i = 0; i < mapping.length; i++){
				String value = mapping[i];
				if (value.equals(fieldVal)){
					return palette[i];
				}
			}
			return 0;
		}
	}
	
	class ItemState{
		VisualItem item;
		String type;
		int color;
		public ItemState(VisualItem item, String type, int color){
			this.item = item;
			this.type = type;
			this.color = color;
		}
	}
	
	
	class SearchBoxListener implements ActionListener{
		JTextField text;
		
		public SearchBoxListener(JTextField text){
			this.text = text;
		}
		
		public SearchBoxListener(){
			
		}
		
		public void repaintGraph(){
			vis.run("repaint");
		}
		
		public void clearPreviousState(){
			for (ItemState item: previousState){
				if (item.type.equals("Node")){
					int color = nodeColorAction.getColor(item.item);
					item.item.setFillColor(color);
				}else if (item.type.equals("Edges")){
					int color = edgeColorAction.getColor(item.item);
					item.item.setStrokeColor(color);
				}
			}
			previousState.clear();
		}
		
		public void search(String selText){
			List<StringMatch> sm = null;
			if (selText != null)
				sm = index.getMatches(selText);

			clearPreviousState();
			
			if (sm != null){
				for (StringMatch match: sm){
					int id = match.getTupleId();
					String type = match.getType();
					if(type.equals("Node")){
						Node node = graph.getNode(id);
						VisualItem visualNode = 
							vis.getVisualItem(graphNodes, node);
						previousState.add(new 
								ItemState(visualNode, type, visualNode.getFillColor()));
						visualNode.setFillColor(FOUND_COLOR);
					}else if (type.equals("Edges")){
						Edge edge = graph.getEdge(id);
						VisualItem visualEdge = 
							vis.getVisualItem(graphEdges, edge);
						previousState.add(new 
								ItemState(visualEdge, type, visualEdge.getStrokeColor()));
						visualEdge.setStrokeColor(FOUND_COLOR);
					}
				}
			}
			repaintGraph();
		}
		
		public void actionPerformed(ActionEvent e){
			String selText = text.getText();
			search(selText);
		}
	}   
	
	class TopQueryListener extends SearchBoxListener implements MouseListener {
		JLabel currentSelected; 
		Border loweredBorder = BorderFactory.createBevelBorder(BevelBorder.LOWERED);
		Border originalBorder;
		
		@Override
		public void mouseClicked(MouseEvent e) {
			
			JLabel sourceButton = (JLabel)e.getSource();
			if (currentSelected != null){
				if (sourceButton == currentSelected){
					super.clearPreviousState();
					currentSelected.setBorder(originalBorder);
					currentSelected = null;
					repaintGraph();
					return;
				}
				currentSelected.setBorder(originalBorder);
			}
			String selText = sourceButton.getText();
			super.search(selText);
			
			currentSelected = sourceButton;
			originalBorder = currentSelected.getBorder();
			currentSelected.setBorder(loweredBorder);
		}

		@Override
		public void mouseEntered(MouseEvent e) {
			
		}

		@Override
		public void mouseExited(MouseEvent e) {
			
		}

		@Override
		public void mousePressed(MouseEvent e) {
		}

		@Override
		public void mouseReleased(MouseEvent e) {
		}

	}

	class SliderListener implements ChangeListener{
		@Override
		public void stateChanged(ChangeEvent evt) {
			JSlider slider = (JSlider)evt.getSource();
			if (!slider.getValueIsAdjusting()) {
		        int val = (int)slider.getValue();
		        RadialCustomLayout.setRadius(val);
		        RadialCustomLayout layout = new RadialCustomLayout(graph, graphGroup);
		        layoutGraph(vis, layout);
		        vis.run("layout");
		    }
			
		}
		
	}
	
	class SearchListener implements ActionListener{
		JTextArea text;
		public SearchListener(JTextArea text){
			this.text = text;
		}
		
		public void actionPerformed(ActionEvent e){
			String selText = text.getSelectedText();
			//System.out.println(selText);
			List<StringMatch> sm = null;
			if (selText != null)
				sm = index.getMatches(selText);

			for (ItemState item: previousState){
				if (item.type.equals("Node")){
					int color = nodeColorAction.getColor(item.item);
					item.item.setFillColor(color);
				}else if (item.type.equals("Edges")){
					int color = edgeColorAction.getColor(item.item);
					item.item.setStrokeColor(color);
				}
			}
			previousState.clear();
			
			if (sm != null){
				for (StringMatch match: sm){
					//System.out.println(match.tupleId + ":" + match.type);
					int id = match.getTupleId();
					String type = match.getType();
					if(type.equals("Node")){
						Node node = graph.getNode(id);
						VisualItem visualNode = 
							vis.getVisualItem(graphNodes, node);
						previousState.add(new ItemState(visualNode, type, visualNode.getFillColor()));
						visualNode.setFillColor(FOUND_COLOR);
					}else if (type.equals("Edges")){
						Edge edge = graph.getEdge(id);
						VisualItem visualEdge = 
							vis.getVisualItem(graphEdges, edge);
						previousState.add(new ItemState(visualEdge, type, visualEdge.getStrokeColor()));
						visualEdge.setStrokeColor(FOUND_COLOR);
					}
				}
			}
			vis.run("repaint");
		}
	}   

	class ToolTipPopup extends FocusControl{
		JFrame tip;
		public void itemClicked(VisualItem item, java.awt.event.MouseEvent e){
			if(e.getButton() == e.BUTTON1)
				return;
			
			Tuple source = item.getSourceTuple();
			
			if (source.canGet("domain", String.class)){
				tip = new JFrame(source.getString("domain"));
			}else{
				tip = new JFrame(source.getString("sourceName") + ">" + source.getString("targetName"));
			}
			tip.setAlwaysOnTop(true);
			tip.setDefaultCloseOperation(tip.DISPOSE_ON_CLOSE);
			JPanel panel = new JPanel();
			tip.setContentPane(panel);
			int left = e.getX() + 100;
			int top = e.getY() - 150;
			
			if (left < 0) left = 0;
			if (top < 0) top = 0;
			
			Rectangle frameBounds = prefuseFrame.getBounds();
			int frameRight = frameBounds.x + frameBounds.width;
			int frameBottom = frameBounds.y + frameBounds.height;

			if (left > frameRight || left + TIP_WIDTH > frameRight){
				left = frameRight - TIP_WIDTH;
			}
			
			if (top > frameBottom || top + TIP_HEIGHT > frameBottom){
				top = frameBottom - TIP_HEIGHT;
			}
			
			tip.setBounds(left, top, TIP_WIDTH, TIP_HEIGHT);
			panel.setBounds(new Rectangle(0, 0, TIP_WIDTH - 100, TIP_HEIGHT - 100));
			panel.setBackground(new Color(255, 255, 255));
			
			JTextArea textArea = new JTextArea();
			
			textArea.setLineWrap(true);
			textArea.setWrapStyleWord(true);
			textArea.setBounds(0, 0 , TIP_WIDTH - 250, TIP_HEIGHT - 100);
			JScrollPane scrollPane = new JScrollPane(textArea); 
			textArea.setEditable(false);
			panel.add(scrollPane);

			panel.add(textArea);
			JButton search = new JButton("search");
			panel.add(search);
			search.addActionListener(new SearchListener(textArea));
			int columns = source.getColumnCount();
			
			for(int i = 0; i < columns; i++){
				String name = source.getColumnName(i);
				Object valObj = source.get(i);
				if (valObj != null){
					String val = valObj.toString();
					if (!val.equals("")){
						String text = name +":"+val;
						textArea.append(text+'\n'+'\n');
					}
				}
			}
			tip.pack();
			tip.setVisible(true);
		}
	}
	
	public void setRanker(NodeRanker ranker){
		this.ranker = ranker;
	}
	
	public void setSearchIndex(Indexer index){
		this.index = index;
	}
	
	public void fillResultPanel(){
		if (this.index != null){
			int num = NUM_DISPLAY;
			
			if (Arguments.hasArg("numsearches")){
				num = Integer.parseInt(Arguments.getArg("numsearches"));
			}
			Border labelBorder = BorderFactory.createBevelBorder(BevelBorder.RAISED,
								new Color(255, 255, 255, 10), new Color(0, 0, 0, 10));
			JSeparator seperate = new JSeparator();
			
			ArrayList<String> topStrings = index.getTopStrings(num);
			GridLayout grid = new GridLayout(num, 1);
			resultPanel.setLayout(grid);
			TopQueryListener topqueries = new TopQueryListener();
			for (String topString : topStrings){
				JLabel label = new JLabel(topString);
				label.setBackground(ColorLib.getColor(255, 255, 255));
				
				label.setBorder(labelBorder);
				label.setHorizontalAlignment(SwingConstants.CENTER);
				
				resultPanel.add(label);
				label.addMouseListener(topqueries);
			}
		}
	}
	
	public PrefuseVis(Graph graph,  
			String labelField, 
			String nodeColorField, 
			String edgeColorField) {
		this.graph = graph;
		this.labelField = labelField;
		this.nodeColorField = nodeColorField;
		this.edgeColorField = edgeColorField;
		this.index = new PrefuseIndexer();
	}
	
	
	void layoutGraph(Visualization vis, Layout layout){
		ActionList layoutActions = new ActionList();
		//adding layout actions
		layoutActions.add(layout);
		layoutActions.add(repaint);

		vis.putAction("layout", layoutActions);
	}
	
	
	public void startVisualization(int ON_CLOSE){
		vis = new Visualization();
		vis.add(graphGroup, graph);
		
		// predicates
		Predicate edgePredicate = new InGroupPredicate(graphEdges);

		// instatiating renderers
		//LabelRenderer labelRenderer = new LabelRenderer(labelField);
		LabelRenderer labelRenderer = new NormalLabelRenderer(labelField);
		//LabelRenderer labelRenderer = new RoundLabelRenderer(labelField);
		EdgeRenderer edgeRenderer = 
			new EdgeRenderer(Constants.EDGE_TYPE_CURVE, Constants.EDGE_ARROW_FORWARD);
			
		// modify the renderers
		labelRenderer.setRenderType(AbstractShapeRenderer.RENDER_TYPE_FILL);
		labelRenderer.setHorizontalAlignment(Constants.CENTER);
		
		//labelRenderer.setRoundedCorner(8,8);
		edgeRenderer.setArrowType(Constants.EDGE_ARROW_FORWARD);
		edgeRenderer.setArrowHeadSize(5, 5); 
		
		DefaultRendererFactory defaultRenderer = 
			new DefaultRendererFactory(labelRenderer);
		defaultRenderer.add(edgePredicate, edgeRenderer);
		vis.setRendererFactory(defaultRenderer);
		
		// structures
		int[] palette = {ColorLib.gray(220,230), REDIRECT_COLOR};
		int[] fillpalette = {ColorLib.rgb(255,200,0)};
		String[] mapping = {"false", "true"};
		String linear = "linear";
		
		// create the actions
		
		// color actions
		nodeColorAction = 
			new NodeElementColorAction(graphNodes, nodeColorField, 
					Constants.NOMINAL, 
					VisualItem.FILLCOLOR, palette, mapping);
		
		ColorAction nodeTextColorAction = 
			new ColorAction(graphNodes, VisualItem.TEXTCOLOR, ColorLib.gray(0));
		
		edgeColorAction = 
			new ElementColorAction(graphEdges, edgeColorField, 
					Constants.NOMINAL, 
					VisualItem.STROKECOLOR, palette, mapping);
		
		DataColorAction edgeFillColorAction = 
			new DataColorAction(graphEdges, edgeColorField, 
					Constants.NOMINAL, 
					VisualItem.FILLCOLOR, fillpalette);
		
		FontAction fonts = new FontAction(graphNodes, 
                FontLib.getFont("Tahoma", 10));
        fonts.add("ingroup('_focus_')", FontLib.getFont("Tahoma", 11));
		
		// layout actions
		Layout layout = new RadialCustomLayout(graph, graphGroup);
		repaint = new RepaintAction();
		
		// animate actions
        ActionList animate = new ActionList(1250);
        animate.setPacingFunction(new SlowInSlowOutPacer());
        animate.add(new QualityControlAnimator());
        animate.add(new ColorAnimator(graphNodes));
        animate.add(new RepaintAction());
		
		// creating action lists
		ActionList colorActions = new ActionList();
		
		ActionList animatePaint = new ActionList(400);
		ActionList repaintActions = new ActionList();
		
		//animation actions
		animatePaint.add(new ColorAnimator(graphNodes));
        animatePaint.add(new RepaintAction());
        
        // repaint actionlist
        repaintActions.add(new RepaintAction());
        
        
        layoutGraph(vis, layout);
        
		// color actions
        colorActions.add(fonts);
        colorActions.add(nodeTextColorAction);
        colorActions.add(nodeColorAction);
        colorActions.add(edgeColorAction);
        colorActions.add(edgeFillColorAction);
		
        // registering actions
		vis.putAction("color", colorActions);
		vis.putAction("animatePaint", animatePaint);
		vis.putAction("animate", animate);
        vis.alwaysRunAfter("color", "animate");
        vis.putAction("repaint", repaintActions);
        
		// creating controls
		SubtreeDragControl subtreeDrag = new SubtreeDragControl();
		PanControl pan = new PanControl();
		ZoomControl zoom = new ZoomControl();
		ToolTipPopup popup = new ToolTipPopup();
		
		// creating the display
		Display display = new Display(vis);
		display.setSize(1500, 800); 
		display.addControlListener(subtreeDrag);
		display.addControlListener(pan);  
		display.addControlListener(zoom); 
		display.addControlListener(popup);
		
		// run vis
		JTextField searchField = new JTextField(1);
		
		JPanel searchPanel = new JPanel();
		
		resultPanel = new JPanel();
		
		
		fillResultPanel();
		
		JButton searchButton = new JButton("search");
		GroupLayout searchPanelLayout = new GroupLayout(searchPanel);
		searchPanel.setLayout(searchPanelLayout);
		
		JSeparator seperate = new JSeparator();
		
		JSlider radiusChanger = new JSlider(JSlider.HORIZONTAL,
                RadialCustomLayout.MIN_RADIUS, RadialCustomLayout.MAX_RADIUS, 
                RadialCustomLayout.RADIUS);
		
		radiusChanger.addChangeListener(new SliderListener());
		
		searchPanelLayout.setHorizontalGroup(searchPanelLayout.createParallelGroup().
											 addComponent(searchField).
											 addComponent(seperate).
											 addComponent(radiusChanger).
											 addComponent(seperate).
											 addComponent(resultPanel, Alignment.CENTER, 150, 150, 200).
											 addComponent(searchButton, Alignment.CENTER, 150, 150, 200));
		
		
		int num = NUM_DISPLAY;
		
		if (Arguments.hasArg("numsearches")){
			num = Integer.parseInt(Arguments.getArg("numsearches"));
		}
		
		
		searchPanelLayout.setVerticalGroup(searchPanelLayout.createSequentialGroup().
										   addComponent(searchField, 30, 30, 30).
										   addComponent(searchButton).
										   addComponent(seperate, 5, 5, 5).
										   addComponent(radiusChanger).
										   addGap(30).
										   addComponent(seperate, 1, 1, 1).
										   addComponent(resultPanel, 20 * num, 20 * num, 20 * num));
											
		searchPanelLayout.setAutoCreateGaps(true);
		//searchPanel.add(searchField);
		//searchPanel.add(searchButton);
		searchButton.addActionListener(new SearchBoxListener(searchField));
		
		/*
		Box box = new Box(BoxLayout.X_AXIS);
		box.add(Box.createHorizontalStrut(10));
		box.add(Box.createHorizontalGlue());
		box.add(searchField);
		box.add(searchButton);
		box.add(Box.createHorizontalStrut(3));
		 */
		
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(display, BorderLayout.CENTER);
		//panel.add(box, BorderLayout.SOUTH);

		Color BACKGROUND = Color.WHITE;
		Color FOREGROUND = Color.DARK_GRAY;
		UILib.setColor(panel, BACKGROUND, FOREGROUND);

		prefuseFrame = new JFrame("WebCrumbs");
		
		GroupLayout prefuseLayout = new GroupLayout(prefuseFrame.getContentPane());
		prefuseFrame.getContentPane().setLayout(prefuseLayout);
		prefuseLayout.setHorizontalGroup(prefuseLayout.createSequentialGroup().
										  addComponent(panel).
										  addComponent(searchPanel, 150, 150, 200));
		
		prefuseLayout.setVerticalGroup(prefuseLayout.createParallelGroup().
										addComponent(panel).
										addComponent(searchPanel));
		
		
		prefuseFrame.setDefaultCloseOperation(ON_CLOSE);
		prefuseFrame.add(panel);
		prefuseFrame.pack();           
		prefuseFrame.setVisible(true); 

		vis.run("color");  
		vis.run("layout");
	}
	
}
