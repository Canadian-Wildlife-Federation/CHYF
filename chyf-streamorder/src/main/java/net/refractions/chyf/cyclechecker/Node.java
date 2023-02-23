package net.refractions.chyf.cyclechecker;

import java.util.Arrays;

public class Node {

	public enum State {
		OPEN, CLOSED, WORKING
	}

	private Node[] inNodes;
	private Node[] outNodes;
	private String id;

	private State state = State.OPEN;

	public Node(String id) {
		this.id = id;
		inNodes = new Node[0];
		outNodes = new Node[0];
	}

	public Node[] inNodes() {
		return this.inNodes;
	}

	public Node[] outNodes() {
		return this.outNodes;
	}

	public State getState() {
		return this.state;
	}

	public void setState(State state) {
		this.state = state;
	}

	public String getId() {
		return this.id;
	}
	
	public void addInNode(Node n) {
		this.inNodes = Arrays.copyOf(inNodes, inNodes.length + 1);
		this.inNodes[this.inNodes.length - 1] = n;

	}

	public void addOutNode(Node n) {
		this.outNodes = Arrays.copyOf(outNodes, outNodes.length + 1);
		this.outNodes[this.outNodes.length - 1] = n;
	}
}
