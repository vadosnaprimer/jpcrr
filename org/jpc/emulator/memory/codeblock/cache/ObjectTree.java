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
//what size to switch between
// -changing at about 8-16 element from narrow to wide seems to work
// -this balances the number of wide nodes (more wides, much fewer narrows)
//maybe just use a narrow array instead of hashmap??
//better then HashMap
// -have both a hashmap (NarrowNode) and explicit (OctalNode)

// RootNode -- array (0-256 children) (== to wide?)
// BinaryNode -- two childs (0-2 children)
// NarrowNode -- map (2-100? children)
// WideNode -- array (100?-256 children)
// LeafNode -- no children only codeblock
// SingularNode -- one child
// SequenceNode -- one child (indexed by sequence of multiple bytes)


/**
 * Holds Objects in a tree that is indexed by an x86 instruction sequence.
 *
 * @author Mike Moleschi
 */
public class ObjectTree
{
    /** root node of tree */
    private Node root;
    private Hashtable leafIndex;

    /**
     * Makes a new empty tree
     */
    public ObjectTree()
    {
        leafIndex = new Hashtable();
        clearAllObjects();
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
         * @param mem memory holding a sequence of x86 bytes to search for
         * @param offset offset into memory of current x86 byte in sequence
         * @return a reference to the currently valid node (null if this node is empty)
         */
        public abstract Node setNode(Node n, ByteArray mem, int offset, int maxOffset);

        /**
         * Get a child node that represents an index.
         *
         * @param mem memory holding a sequence of x86 bytes to search for
         * @param offset offset into memory of current x86 byte in sequence
         * @return a reference to the child node
         */
        public abstract Node getNode(ByteArray mem, int offset);
   
        /**
         * Get the Object that represents a x86 byte sequence (if it exists).
         *
         * @param mem memory holding a sequence of x86 bytes to search for
         * @param offset offset into memory of current x86 byte in sequence
         * @return a reference to the code obj that represents the x86 sequnce or null if it doesn't exist
         */
        public Object getObject(ByteArray mem, int offset)
        {
            Node child = getNode(mem, offset);
            if (child == null)
                return null;
            return child.getObject(mem, offset+1);
        }

        /**
         * Sets the Object that represents a x86 byte sequence.
         * (overwrites if already exists).  If obj is null, it will remove any
         * codeobj stored for that sequence and remove any unique parts of 
         * that sequence.
         *
         * @param obj code obj representing the sequence of x86 bytes
         * @param mem memory holding a sequence of x86 bytes which the code obj represets
         * @param offset offset into memory of current x86 byte in sequence
         * @param maxOffset offset into memory of last x86 byte in sequence
         * @return a reference to this node, incase this node transforms itself (return null if there are no node indexes in this node)
         */
        public Node setObject(Object obj, ByteArray mem, int offset, int maxOffset)
        {
            Node child = getNode(mem, offset);
            if (child == null)
            {
                child = new SequenceNode(new LeafNode(obj), mem, offset+1, maxOffset);
                return setNode(child, mem, offset, maxOffset);
            }

            Node result = child.setObject(obj, mem, offset+1, maxOffset);
            if (result == child)
                return this;

            return setNode(result, mem, offset, maxOffset);
        }

        /**
         * Allows a visitor to enter the node.  Will call visitNodes in all the
         * node's children.
         *
         * @param visitor visitor for the node
         * @return whether the visitor should continue (if the visitor is complete, allows it to stop)
         * @see ObjectTreeVisitor
         */
        public boolean visitNodes(ObjectTreeVisitor visitor)
        {
            return visitor.visitNode(this);
        }

        public void print(String indent)
        {
            System.out.println(indent+getClass().getName());
        }
    }
    
    public static class WideNode extends Node
    {
        private int valid;
        private Node[] nodes;
        
        /** Create a new wide node */
        WideNode()
        {
            nodes = new Node[0x100];
            valid = 0;
        }

        public int size()
        {
            return valid;
        } 

        public Node setNode(Node n, ByteArray mem, int offset, int maxOffset)
        {
            int idx = 0xFF & mem.getByte(offset);
            if (nodes[idx] == null)
                valid++;
            nodes[idx] = n;
            if (n == null)
                valid--;
            return this;
        }

        public Node getNode(ByteArray mem, int offset)
        {
            int idx = 0xFF & mem.getByte(offset);
            return nodes[idx]; 
        }

        public boolean visitNodes(ObjectTreeVisitor visitor)
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

            return true;
        }
        
        public void print(String indent)
        {
            System.out.println(indent+"Wide Node");
            for (int i=0; i<nodes.length; i++)
            {   
                if (nodes[i] != null)
                {
                    System.out.println(indent+i);
                    nodes[i].print(indent+" ");
                }
            }
        }
    }

    public static class NarrowNode extends Node
    {
        private Hashtable nodes;
        
        /** Create a new narrow node */
        NarrowNode()
        {
            nodes = new Hashtable(0);
        }

        public int size()
        {
            return nodes.size();
        }

        public Node getNode(ByteArray mem, int offset)
        {
            Byte b = new Byte(mem.getByte(offset));
            return (Node) nodes.get(b);
        }

        public Node setNode(Node n, ByteArray mem, int offset, int maxOffset)
        {
            Byte index = new Byte(mem.getByte(offset));
            if (n == null)
            {
                nodes.remove(index);
                if (nodes.size() == 0)
                    return null;
                return this;
            }

            nodes.put(index, n);
            if (nodes.size() <= MAX_NARROW_SIZE)
                return this;

            /* nodes are full, therefore must change to different node type */
            Node replacementNode = new WideNode();
            SimpleByteArray dummy = new SimpleByteArray(1);
            Enumeration enumer = nodes.keys();
            while(enumer.hasMoreElements())
            {
                Byte b = (Byte) enumer.nextElement();
                Node nn = (Node) nodes.get(b);
                dummy.setByte(0, b.byteValue());

                replacementNode = replacementNode.setNode(nn, dummy, 0, 0);
            }

            replacementNode = replacementNode.setNode(n, mem, offset, maxOffset);
            return replacementNode;
        }
        
        public boolean visitNodes(ObjectTreeVisitor visitor)
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
    }

    public static class BinaryNode extends Node
    {
        private Node leftNode, rightNode;
        private byte leftIndex, rightIndex;
        private boolean leftUsed, rightUsed;
        
        
        /** Create a new binary node */
        BinaryNode()
        {
            leftUsed = false;
            rightUsed = false;
        }

        public int size()
        {
            if ((leftUsed) && (rightUsed))
                return 2;
            else if ((leftUsed) || (rightUsed))
                return 1;
            else
                return 0;
        }

        public Node setNode(Node n, ByteArray mem, int offset, int maxOffset)
        {
            byte index = mem.getByte(offset);

            boolean inserted = false;
            if (!leftUsed || (leftUsed && (leftIndex == index)))
            {
                leftIndex = index;
                leftNode = n;
                leftUsed = true;
                if (n == null)
                    leftUsed = false;
                inserted = true;
            }
            else if (!rightUsed || (rightUsed && (rightIndex == index)))
            {
                rightIndex = index;
                rightNode = n;
                rightUsed = true;
                if (n == null)
                    rightUsed = false;
                inserted = true;
            }

            if (size() == 0)
                return null;
            else if (inserted)
                return this;
            
            /* nodes are full, therefore must change to different node type */

            Node replacementNode = new NarrowNode();
            SimpleByteArray dummy = new SimpleByteArray(1);
            dummy.setByte(0, leftIndex);
            replacementNode = replacementNode.setNode(leftNode, dummy, 0, 0);
            dummy.setByte(0, rightIndex);
            replacementNode = replacementNode.setNode(rightNode, dummy, 0, 0);
            replacementNode = replacementNode.setNode(n, mem, offset, maxOffset);

            return replacementNode;
        }
        
        public Node getNode(ByteArray mem, int offset)
        {
            byte index = mem.getByte(offset);

            if ((leftIndex == index) && (leftUsed))
                return leftNode;
            else if ((rightIndex == index) && (rightUsed))
                return rightNode;
            else
                return null;
        }
        
        public boolean visitNodes(ObjectTreeVisitor visitor)
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
            System.out.println(indent+"Binary["+leftIndex+" "+rightIndex+"]");
            if (leftNode != null)
                leftNode.print(indent+" ");
            if (rightNode != null)
                rightNode.print(indent+" ");
        }
    }

    public static class SingularNode extends Node
    {
        private Node childNode;
        private byte childIndex;
        private boolean childUsed;
       
        /** Create a new single node */
        SingularNode()
        {
            childUsed = false;
        }

        public int size()
        {
            if (childUsed)
                return 1;
            return 0;
        }

        public Node setNode(Node n, ByteArray mem, int offset, int maxOffset)
        {
            byte index = mem.getByte(offset);

            if (!childUsed || (childUsed && (childIndex == index)))
            {
                // if only child node is to be null, no point in having this node!
                if (n == null)
                    return null;

                childIndex = index;
                childNode = n;
                childUsed = true;
                return this;
            }

            /* nodes are full, therefore must change to different node type */

            Node replacementNode = new BinaryNode();
            SimpleByteArray dummy = new SimpleByteArray(1);
            dummy.setByte(0, childIndex);
            replacementNode = replacementNode.setNode(childNode, dummy, 0, 0);
            replacementNode = replacementNode.setNode(n, mem, offset, maxOffset);

            return replacementNode;
        }

        public Node getNode(ByteArray mem, int offset)
        {
            if (childIndex == mem.getByte(offset))
                return childNode;
            else
                return null;
        }
        
        public boolean visitNodes(ObjectTreeVisitor visitor)
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
            System.out.println(indent+"SINGLE!!!!");
        }
    }

    public static class SequenceNode extends Node
    {
        byte[] sequence;
        Node child;

        public SequenceNode(Node child, ByteArray arr, int offset, int maxOffset)
        {
            this.child = child;
            sequence = new byte[maxOffset - offset + 1];
            arr.copyContentsInto(offset, sequence, 0, sequence.length);
        }

        public int size()
        {
            return 1;
        }

        public Node setNode(Node n, ByteArray mem, int offset, int maxOffset)
        {
            for (int i=0; i<sequence.length; i++)
            {
                if (mem.getByte(offset + i) == sequence[i])
                    continue;

                BinaryNode branch = new BinaryNode();
                SimpleByteArray dummy = new SimpleByteArray(sequence);

                Node left = null;
                if (i+1 == sequence.length)
                    left = child;
                else
                    left = new SequenceNode(child, dummy, i+1, sequence.length-1);
                
                Node right = null;
                if (offset+i == maxOffset)
                    right = n;
                else
                    right = new SequenceNode(n, mem, offset+i+1, maxOffset);

                branch.setNode(left, dummy, i, i);
                branch.setNode(right, mem, offset+i, offset+i);

                if (i == 0)
                    return branch;
                return new SequenceNode(branch, dummy, 0, i-1);
            }
            
            if (n == null)
                return null;
            child = n;
            return this;
        }

        public Node getNode(ByteArray mem, int offset)
        {
            for (int i=0; i<sequence.length; i++)
            {
                if (mem.getByte(offset + i) == sequence[i])
                    continue;
                return null;
            }

            return child;
        }
   
        public Object getObject(ByteArray mem, int offset)
        {
            Node child = getNode(mem, offset);
            if (child == null)
                return null;
            return child.getObject(mem, offset+sequence.length);
        }

        public Node setObject(Object obj, ByteArray mem, int offset, int maxOffset)
        {
            Node child = getNode(mem, offset);
            if (child == null)
                return setNode(new LeafNode(obj), mem, offset, maxOffset);

            Node result = child.setObject(obj, mem, offset+sequence.length, maxOffset);
            if (result == child)
                return this;

            return setNode(result, mem, offset, maxOffset);
        }

        public void print(String indent)
        {
	    String arrayString = "";
	    if (sequence == null)
		arrayString = "[null]";
	    else {
		for (int i = 0; i < sequence.length; i++)
		    arrayString += String.valueOf(sequence[i]) + ",";
		arrayString += "]";
	    }

            System.out.println(indent+"SEQ"+arrayString);
            child.print(indent+" ");
        }
    }

    public static class LeafNode extends Node
    {
        private int useageCount;
        private Object obj;
        
        /** Create a new leaf node */
        LeafNode(Object obj)
        {
            useageCount = 1;
            this.obj = obj;
        }

        public Node setNode(Node n, ByteArray mem, int offset, int maxOffset)
        {
            throw new IllegalStateException("Setting node on a leaf");
        }

        public Node getNode(ByteArray mem, int offset)
        {
            throw new IllegalStateException("Getting a node from a leaf");
        }

        public Object getObject(ByteArray mem, int offset)
        {
            useageCount++;
            return obj;
        }

        public Node setObject(Object obj, ByteArray mem, int offset, int maxOffset)
        {
            useageCount = 1;
            this.obj = obj;
            return this;
        }

        public int size()
        {
            return 0;
        }

        public int getUsageCount()
        {
            return useageCount;
        }

        public Object peekObject()
        {
            return obj;
        }

        public void print(String indent)
        {
            System.out.println(indent+"Leaf["+obj+"]");
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
     * Get the Object that represents a x86 byte sequence (if it exists).
     *
     * @param mem memory holding a sequence of x86 bytes to search for
     * @param offset offset into memory of current x86 byte in sequence
     * @return a pointer to the code obj that represents the x86 sequnce or null if it doesn't exist
     */
    public Object getObject(ByteArray mem, int offset)
    {
        return root.getObject(mem, offset);
    }

    /**
     * Sets the Object that represents a x86 byte sequence.
     * (overwrites if already exists)
     *
     * @param obj code obj representing the sequence of x86 bytes
     * @param mem memory holding a sequence of x86 bytes to search for
     * @param offset offset into memory of current x86 byte in sequence
     * @param length x86 length of obj
     */
    public void setObject(ByteArray mem, int offset, Object obj, int length)
    {
        root = root.setObject(obj, mem, offset, offset + length - 1);
    }

    /**
     * Visits all nodes in the tree with a visitor
     *
     * @param visitor visitor for the nodes
     * @see ObjectTreeVisitor
     */
    public void visitNodes(ObjectTreeVisitor visitor)
    {
        if (root != null)
            root.visitNodes(visitor);
    }

    public void printTree()
    {
        root.print("");
    }
}




