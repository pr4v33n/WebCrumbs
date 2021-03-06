package edu.stanford.webcrumbs;

/*
 * Author : Subodh Iyengar
 */

import java.util.List;

import javax.swing.JFrame;

import prefuse.data.Edge;
import prefuse.data.Graph;
import edu.stanford.webcrumbs.Arguments;
import edu.stanford.webcrumbs.graph.PrefuseGraphBuilder;
import edu.stanford.webcrumbs.graph.GraphBuilder;
import edu.stanford.webcrumbs.graph.PrefuseGraphBuilder;
import edu.stanford.webcrumbs.graph.PrefuseToJUNG;
import edu.stanford.webcrumbs.graph.search.PrefuseIndexer;
import edu.stanford.webcrumbs.graph.search.Indexer;
import edu.stanford.webcrumbs.visualization.PrefuseVis;
import edu.stanford.webcrumbs.visualization.Visualization;
import edu.stanford.webcrumbs.data.Connection;
import edu.stanford.webcrumbs.data.Page;
import edu.stanford.webcrumbs.parsers.FourthPartyDataFileParser;
import edu.stanford.webcrumbs.parsers.FourthPartyParser;
import edu.stanford.webcrumbs.parsers.HarParser;
import edu.stanford.webcrumbs.parsers.Parser;
import edu.stanford.webcrumbs.util.UrlUtil;

public class Main {
	public static void runSimulation(int ON_CLOSE) throws Exception{
		
		// initializes the domain map
		UrlUtil.createDomainMap();
		
		Parser parser;
		
		// initialize the parser
		
		if (Arguments.hasType()){
			if (Arguments.getType().equals("har")){
				parser = new HarParser();
			}
			else if (Arguments.getType().equals("fourthparty")){
				parser = new FourthPartyParser();
			}
			else if (Arguments.getType().equals("fourthpartydatafile")){
				parser = new FourthPartyDataFileParser();
			}
			else{
				// fall back just provide class name
				Class parserClass = Class.forName(Arguments.getType());
				parser = (Parser) parserClass.newInstance();
			}
		}else{
			throw new Exception("no type specified");
		}
		
		String taintDepth = Arguments.getArg("taintDepth");
		try{
			Page.TAINT_DEPTH = Integer.parseInt(taintDepth);
		}catch (Exception e){
			throw new Exception("invalid taint depth value");
		}
		
		List<Page> pages = parser.parse();
		Page.setPages(pages);
		
		// if prefuse
		GraphBuilder<Graph> graphbuilder = new PrefuseGraphBuilder();
		
		Graph prefuseGraph = graphbuilder.createGraph(pages);
	
		// if prefuse
		Indexer search = new PrefuseIndexer();
		search.buildIndex(prefuseGraph);
		
		// if prefuse
		// TODO: support more 
		PrefuseVis p_vis = new PrefuseVis(prefuseGraph, "domain", "root", "redirect");
		
		p_vis.setSearchIndex(search);
		if (Arguments.hasNodeRanker()){
			p_vis.setRanker(Arguments.getNodeRanker());
		}
		
		if (Arguments.hasNodeRanker()){	
			Arguments.getNodeRanker().run();
		}
		
		Visualization vis = p_vis; 		
		vis.startVisualization(ON_CLOSE);
	}
	
	public static void main(String[] args){
		try {
			Arguments.parse(args);
			runSimulation(JFrame.EXIT_ON_CLOSE);
		} catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
			System.exit(-1);
		}
	}
}
