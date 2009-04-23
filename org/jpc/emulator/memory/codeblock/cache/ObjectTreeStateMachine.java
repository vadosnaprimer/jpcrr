/*
    JPC: A x86 PC Hardware Emulator for a pure Java Virtual Machine
    Release Version 2.0

    A project from the Physics Dept, The University of Oxford

    Copyright (C) 2007 Isis Innovation Limited

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License version 2 as published by
    the Free Software Foundation.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along
    with this program; if not, write to the Free Software Foundation, Inc.,
    51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 
    Details (including contact information) can be found at: 

    www.physics.ox.ac.uk/jpc
*/

package org.jpc.emulator.memory.codeblock.cache;

import java.util.*;
import org.jpc.emulator.memory.*;


/**
 * Holds Objects in a tree that is indexed by an integer sequence.
 *
 * @author Mike Moleschi
 */
public class ObjectTreeStateMachine
{
    /** root node of tree */
    private Node root;
    private Node currentNode;
    private Node currentParent;
    private int parentIndex;

    /**
     * Makes a new empty tree
     */
    public ObjectTreeStateMachine()
    {
        clearAllObjects();
        resetTreeState();
    }

    /**
     * Defines a node in the tree.
     */
    public static abstract class Node
    {
        /**
         * Defines at what size a Narrow Node should grow to before it changes
         * itself to a wide node.
         */
        static final int MAX_NARROW_SIZE = 8;
        static final int MAX_WIDE_SIZE = 700;

        protected int useageCount;
        protected Object obj;
        
        Node()
        {
            useageCount = 0;
            this.obj = null;
        }

        Node(Object obj)
        {
            useageCount = 1;
            this.obj = obj;
        }

        /**
         * Return the size of the node (number of children)
         *
         * @return number of children this node indexes
         */
        public abstract int size();

        /**
         * Add a child node to represent an index. (or replace if index is
         * already used) <br/> 
         * Because this may add more children than the structure can handle,
         * the current node may have to replace itself!  A reference to the
         * resulting valid structure is returned. (this could be the same, or
         * it could be a new replacement node)<br/>
         * If the node passed is null, its reference and index will be 
         * cleared.  If that means this node is then emtpy, it will return null
         * so that the parent can remove it.  In this way a dead end path will 
         * be cleared.
         *
         * @param n node to add as child to current node
         * @param index current element of indexing sequence
         * @return a reference to the currently valid node (null if this node is empty)
         */
        public abstract Node setNode(Node n, int index);

        /**
         * Get a child node that represents an index.
         *
         * @param index current element of indexing sequence
         * @return a reference to the child node
         */
        public abstract Node getNode(int index);
   
        /**
         * Get the Object that represents a x86 byte sequence (if it exists).
         *
         * @param index current element of indexing sequence
         * @return a reference to the code obj that represents the x86 sequnce or null if it doesn't exist
         */
        public Object getObject()
        {
            useageCount++;
            return obj;
        }

        /**
         * Sets the Object that represents a x86 byte sequence.
         * (overwrites if already exists).  If obj is null, it will remove any
         * codeobj stored for that sequence and remove any unique parts of 
         * that sequence.
         *
         * @param obj code obj representing the sequence of x86 bytes
         * @param index current element of indexing sequence
         * @return a reference to this node, incase this node transforms itself (return null if there are no node indexes in this node)
         */
        public void setObject(Object obj)
        {
            useageCount = 1;
            this.obj = obj;
        }

        public int getUsageCount()
        {
            return useageCount;
        }

        public Object peekObject()
        {
            return obj;
        }

        /**
         * Allows a visitor to enter the node.  Will call visitNodes in all the
         * node's children.
         *
         * @param visitor visitor for the node
         * @return whether the visitor should continue (if the visitor is complete, allows it to stop)
         * @see ObjectTreeStateMachineVisitor
         */
        public boolean visitNodes(ObjectTreeStateMachineVisitor visitor)
        {
            return visitor.visitNode(this);
        }

        public void print(String indent)
        {
            System.out.println(indent + getClass().getName() + "(" + size() + ")");
        }
    }
    
    public static class WideNode extends Node
    {
        private int valid;
        private Node[] nodes;
        private Hashtable mappedNodes;
        
        /** Create a new wide node */
        WideNode()
        {
            super();
            nodes = new Node[MAX_WIDE_SIZE];
            valid = 0;
            mappedNodes = null;
        }

        public int size()
        {
            if (mappedNodes == null)
                return valid;
            return mappedNodes.size() + valid;
        } 

        public Node setNode(Node n, int index)
        {
            try
            {
                if (nodes[index] == null)
                    valid++;
                nodes[index] = n;
                if (n == null)
                    valid--;
            } 
            catch (ArrayIndexOutOfBoundsException e)
            {
                if (mappedNodes == null)
                    mappedNodes = new Hashtable(0);
                mappedNodes.put(new Integer(index), n);
            }
            return this;
        }

        public Node getNode(int index)
        {
            try
            {
                return nodes[index]; 
            } 
            catch (ArrayIndexOutOfBoundsException e)
            {
                if (mappedNodes != null)
                    return (Node) mappedNodes.get(new Integer(index));
                return null;
            }
        }

        public boolean visitNodes(ObjectTreeStateMachineVisitor visitor)
        {
            if (!super.visitNodes(visitor))
                return false;

            for(int i = 0; i < nodes.length; i++)
            {
                if (nodes[i] == null)
                    continue;
                if (!nodes[i].visitNodes(visitor))
                    return false;
            }
            
            if (mappedNodes != null)
            {
                Enumeration enumer = mappedNodes.keys();
                while (enumer.hasMoreElements())
                {
                    Integer i = (Integer) enumer.nextElement();
                    
                    Node child = (Node) mappedNodes.get(i);
                    if (!((Node) mappedNodes.get(i)).visitNodes(visitor))
                        return false;
                }
            }

            return true;
        }
        
        public void print(String indent)
        {
            super.print(indent);

            for (int i=0; i<nodes.length; i++)
            {   
                if (nodes[i] != null)
                {
                    System.out.print(indent+i);
                    nodes[i].print(indent+" ");
                }
            }

            if (mappedNodes != null)
            {
                Enumeration enumer = mappedNodes.keys();
                System.out.print("[");
                while (enumer.hasMoreElements())
                {
                    Integer i = (Integer) enumer.nextElement();
		    ((Node) mappedNodes.get(i)).print(indent+" ");
                    System.out.print(i + ", ");
                }
                System.out.println("]");
            }
        }
    }

    public static class NarrowNode extends Node
    {
        private Hashtable nodes;
        
        /** Create a new narrow node */
        NarrowNode()
        {
            super();
            nodes = new Hashtable(0);
        }

        public int size()
        {
            return nodes.size();
        }

        public Node setNode(Node n, int index)
        {
            if (n == null)
            {
                nodes.remove(new Integer(index));
                return this;
            }

            nodes.put(new Integer(index), n);
            if (nodes.size() <= MAX_NARROW_SIZE)
                return this;

            /* nodes are full, therefore must change to different node type */
            Node replacementNode = new WideNode();
            Enumeration enumer = nodes.keys();
            while(enumer.hasMoreElements())
            {
                Integer i = (Integer) enumer.nextElement();
                replacementNode = replacementNode.setNode((Node) nodes.get(i), i.intValue());
            }

            replacementNode = replacementNode.setNode(n, index);
            return replacementNode;
        }
        
        public Node getNode(int index)
        {
            return (Node) nodes.get(new Integer(index));
        }

        public boolean visitNodes(ObjectTreeStateMachineVisitor visitor)
        {
            if (!super.visitNodes(visitor))
                return false;

            Enumeration enumer = nodes.keys();
            while (enumer.hasMoreElements())
            {
                Integer i = (Integer) enumer.nextElement();

                Node child = (Node) nodes.get(i);
                if (!((Node) nodes.get(i)).visitNodes(visitor))
                    return false;
            }

            return true;
        }

        public void print(String indent)
        {
            super.print(indent);

            Enumeration enumer = nodes.keys();
            System.out.print("[");
            while (enumer.hasMoreElements())
            {
                Integer i = (Integer) enumer.nextElement();
                ((Node) nodes.get(i)).print(indent+" ");
                System.out.print(i + ", ");
            }
        }
    }

    public static class BinaryNode extends Node
    {
        private Node leftNode, rightNode;
        private int leftIndex, rightIndex;
        
        
        /** Create a new binary node */
        BinaryNode()
        {
            super();
            leftNode = rightNode = null;
        }
        
        public int size()
        {
            if ((leftNode != null) && (rightNode != null))
                return 2;
            else if ((leftNode != null) || (rightNode != null))
                return 1;
            else
                return 0;
        }

        public Node setNode(Node n, int index)
        {
            boolean inserted = false;
            if ((leftNode == null) || (leftIndex == index))
            {
                leftIndex = index;
                leftNode = n;
                inserted = true;
            }
            else if ((rightNode == null) || (rightIndex == index))
            {
                rightIndex = index;
                rightNode = n;
                inserted = true;
            }

            if (inserted)
                return this;
            
            
            /* nodes are full, therefore must change to different node type */
            Node replacementNode = new NarrowNode();
            replacementNode = replacementNode.setNode(leftNode, leftIndex);
            replacementNode = replacementNode.setNode(rightNode, rightIndex);
            replacementNode = replacementNode.setNode(n, index);
            return replacementNode;
        }
        
        public Node getNode(int index)
        {
            if (leftIndex == index)
                return leftNode;
            else if (rightIndex == index)
                return rightNode;
            else
                return null;
        }
        
        public boolean visitNodes(ObjectTreeStateMachineVisitor visitor)
        {
            if (!super.visitNodes(visitor))
                return false;
            
            if (rightNode != null)
            {
                if (!rightNode.visitNodes(visitor))
                    return false;
            }
            if (leftNode != null)
            {
                if (!leftNode.visitNodes(visitor))
                    return false;
            }
            
            return true;
        }
        
        public void print(String indent)
        {
            super.print(indent);
            System.out.println(indent+"["+leftIndex+" "+rightIndex+"]");
            if (leftNode != null)
                leftNode.print(indent+" ");
            if (rightNode != null)
                rightNode.print(indent+" ");
        }
    }
    
    public static class SingularNode extends Node
    {
        private Node childNode;
        private int childIndex;
        
        /** Create a new single node */
        SingularNode()
        {
            super();
            childNode = null;
        }
        
        public int size()
        {
            if (childNode != null)
                return 1;
            return 0;
        }
        
        public Node setNode(Node n, int index)
        {
            if ((childNode == null) || (childIndex == index))
            {
                childIndex = index;
                childNode = n;
                return this;
            }

            /* nodes are full, therefore must change to different node type */

            Node replacementNode = new BinaryNode();
            replacementNode = replacementNode.setNode(childNode, childIndex);
            replacementNode = replacementNode.setNode(n, index);
            return replacementNode;
        }

        public Node getNode(int index)
        {
            if (childIndex == index)
                return childNode;
            else
                return null;
        }
        
        public boolean visitNodes(ObjectTreeStateMachineVisitor visitor)
        {
            if (!super.visitNodes(visitor))
                return false;

            if (childNode != null)
            {
                if (!childNode.visitNodes(visitor))
                    return false;
            }
            
            return true;
        }
    
        public void print(String indent)
        {
            super.print(indent);
            System.out.println(indent+"["+childIndex+"]");
            if (childNode != null)
                childNode.print(indent+" ");
        }
    }
    
    /**
     * Clear the entire tree.
     */
    public void clearAllObjects()
    {
        root = new WideNode();
    }
    
    /**
     * Reset tree state to the top of the tree
     */
    public void resetTreeState()
    {
        currentNode = root;
        currentParent = null;
    }
    
    /**
     * Step the tree to the next node in the tree along the path defined by index
     * Create a path if one doesn't exist
     *
     * @param index route to take to the next node in the tree.
     * @return true if the step is to an existing node, false if the step adds a node to the tree
     */
    public boolean stepTree(int index)
    {
        if (currentNode == null)
            throw new IllegalStateException("Null Current Node?!");

        Node child = currentNode.getNode(index);
        boolean childCreated = false;
        if (child == null)
        {   // must create a new node
            childCreated = true;
            child = new SingularNode();
            Node replacementNode = currentNode.setNode(child, index);
            if (replacementNode == null)  // check if all hell broke loose
                throw new IllegalStateException("Null Current Node?!");

            if ((currentParent != null) && (replacementNode != currentNode))
            {
                currentParent.setNode(replacementNode, parentIndex);
                currentNode = replacementNode;
            }
        }
        currentParent = currentNode;
        parentIndex = index;
        currentNode = child;
        return (!(childCreated));
    }
        
    /**
     * Get the Object to be found from current state of tree
     *
     * @return a pointer to the obj that represents the index sequnce or null if it doesn't exist
     */
    public Object getObjectAtState()
    {
        if (currentNode == null)
            return null;
        return currentNode.getObject();
    }
    
    /**
     * Sets the Object at the current state of the tree.
     * (overwrites if already exists)
     *
     * @param obj code obj representing the sequence of x86 bytes
     */
    public void setObjectAtState(Object obj)
    {
        if (currentNode == null)
            throw new IllegalStateException("Cannot Add Object To null Node");

        currentNode.setObject(obj);
    }

    /**
     * Visits all nodes in the tree with a visitor
     *
     * @param visitor visitor for the nodes
     * @see ObjectTreeStateMachineVisitor
     */
    public void visitNodes(ObjectTreeStateMachineVisitor visitor)
    {
        if (root != null)
            root.visitNodes(visitor);
    }

    public void printTree()
    {
        root.print("");
    }
}




