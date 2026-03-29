/*
 * Copyright 2006-2009, 2017, 2020 United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 *
 * The NASA World Wind Java (WWJ) platform is licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * NASA World Wind Java (WWJ) also contains the following 3rd party Open Source
 * software:
 *
 *     Jackson Parser – Licensed under Apache 2.0
 *     GDAL – Licensed under MIT
 *     JOGL – Licensed under  Berkeley Software Distribution (BSD)
 *     Gluegen – Licensed under Berkeley Software Distribution (BSD)
 *
 * A complete listing of 3rd Party software notices and licenses included in
 * NASA World Wind Java (WWJ)  can be found in the WorldWindJava-v2.2 3rd-party
 * notices and licenses PDF found in code directory.
 */

package gov.nasa.worldwind.util.tree;

import java.beans.PropertyChangeEvent;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import gov.nasa.worldwind.WWObjectImpl;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.render.BasicWWTexture;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.WWUtil;

/**
 * Default implementation of a {@link TreeNode}.
 *
 * @author pabercrombie
 * @version $Id: BasicTreeNode.java 1171 2013-02-11 21:45:02Z dcollins $
 */
public class BasicTreeNode extends WWObjectImpl implements TreeNode
{
    protected String text;
    protected Object imageSource;
    protected BasicWWTexture texture;

    protected String description;

    protected TreeNode parent;
    protected List<TreeNode> children; // List is created when children are added

    protected boolean enabled = true;
    protected boolean selected;
    protected boolean visible = true;

    /**
     * Flag to indicate that any part of the sub-tree rooted at this node is selected. This value is computed on demand
     * and cached.
     */
    protected String treeSelected;

    /**
     * Create a node with text.
     *
     * @param text Node text.
     */
    public BasicTreeNode(String text)
    {
        this(text, null);
    }

    /**
     * Create a node with text and an icon.
     *
     * @param text        Node text.
     * @param imageSource Image source for the node icon. May be a String, URL, or BufferedImage.
     */
    public BasicTreeNode(String text, Object imageSource)
    {
        if (text == null)
        {
            String message = Logging.getMessage("nullValue.StringIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.text = text.trim();
        this.setImageSource(imageSource);
    }

    /** {@inheritDoc} */
    @Override
	public String getText()
    {
        return this.text;
    }

    /** {@inheritDoc} */
    @Override
	public TreeNode getParent()
    {
        return this.parent;
    }

    /** {@inheritDoc} */
    @Override
	public void setParent(TreeNode node)
    {
        this.parent = node;
    }

    /** {@inheritDoc} */
    @Override
	public Iterable<TreeNode> getChildren()
    {
        if (this.children != null)
            return Collections.unmodifiableList(this.children);
        else
            return Collections.emptyList();
    }

    /** {@inheritDoc} */
    @Override
	public boolean isEnabled()
    {
        return this.enabled;
    }

    /** {@inheritDoc} */
    @Override
	public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    /** {@inheritDoc} */
    @Override
	public boolean isSelected()
    {
        return this.selected;
    }

    /** {@inheritDoc} */
    @Override
	public void setSelected(boolean selected)
    {
        boolean prevSelected = this.isSelected();
        this.selected = selected;
        this.treeSelected = null; // Need to recompute tree selected field

        if (prevSelected != selected)
            this.firePropertyChange(AVKey.TREE_NODE, null, this);
    }

    /** {@inheritDoc} */
    @Override
	public String isTreeSelected()
    {
        if (this.treeSelected == null)
            this.treeSelected = this.computeTreeSelected();

        return this.treeSelected;
    }

    /**
     * Determine if any part of the sub-tree rooted at this node is selected.
     *
     * @return {@link #SELECTED}, {@link #NOT_SELECTED}, {@link #PARTIALLY_SELECTED}.
     */
    protected String computeTreeSelected()
    {
        String selected = this.isSelected() ? SELECTED : NOT_SELECTED;

        for (TreeNode child : this.getChildren())
        {
            String childSelected = child.isTreeSelected();

            if (!selected.equals(childSelected))
            {
                selected = PARTIALLY_SELECTED;
                break; // No need to look at other nodes
            }
        }

        return selected;
    }

    /** {@inheritDoc} */
    @Override
	public boolean isVisible()
    {
        return this.visible;
    }

    /** {@inheritDoc} */
    @Override
	public boolean isLeaf()
    {
        return WWUtil.isEmpty(this.children);
    }

    /** {@inheritDoc} */
    @Override
	public void setVisible(boolean visible)
    {
        this.visible = visible;
    }

    @Override
	public String getDescription()
    {
        return description;
    }

    @Override
	public void setDescription(String description)
    {
        this.description = description != null ? description.trim() : null;
    }

    /** {@inheritDoc} */
    @Override
	public Object getImageSource()
    {
        return imageSource;
    }

    /** {@inheritDoc} */
    @Override
	public void setImageSource(Object imageSource)
    {
        this.imageSource = imageSource;
        this.texture = null;
    }

    /** {@inheritDoc} */
    @Override
	public boolean hasImage()
    {
        return this.getImageSource() != null;
    }

    /** {@inheritDoc} */
    @Override
	public BasicWWTexture getTexture()
    {
        if (this.texture == null)
            this.initializeTexture();

        return this.texture;
    }

    /**
     * Create and initialize the texture from the image source. If the image is not in memory this method will request
     * that it be loaded.
     */
    protected void initializeTexture()
    {
        Object imageSource = this.getImageSource();
        if (imageSource instanceof String || imageSource instanceof URL)
        {
            URL imageURL = WorldWind.getDataFileStore().requestFile(imageSource.toString());
            if (imageURL != null)
            {
                this.texture = new BasicWWTexture(imageURL, true);
            }
        }
        else if (imageSource != null)
        {
            this.texture = new BasicWWTexture(imageSource, true);
        }
    }

    /** {@inheritDoc} */
    @Override
	public void addChild(TreeNode child)
    {
        if (this.children == null)
            this.children = new ArrayList<>();
        this.addChild(this.children.size(), child);
    }

    /** {@inheritDoc} */
    @Override
	public void addChild(int index, TreeNode child)
    {
        if (this.children == null)
            this.children = new ArrayList<>();
        this.children.add(index, child);

        this.treeSelected = null;  // Need to recompute tree selected field
        child.setParent(this);
        child.addPropertyChangeListener(this);
        this.firePropertyChange(AVKey.TREE_NODE, null, this);
    }

    /** {@inheritDoc} */
    @Override
	public void removeChild(TreeNode child)
    {
        if (this.children != null)
            this.children.remove(child);

        if (child != null && child.getParent() == this)
        {
            this.treeSelected = null;  // Need to recompute tree selected field
            child.setParent(null);
            child.removePropertyChangeListener(this);
            this.firePropertyChange(AVKey.TREE_NODE, null, this);
        }
    }

    /** {@inheritDoc} */
    @Override
	public void removeAllChildren()
    {
        if (this.children == null)
            return;

        var iterator = this.children.iterator();
        if (!iterator.hasNext())
            return;

        while (iterator.hasNext())
        {
            TreeNode child = iterator.next();
            iterator.remove();

            child.setParent(null);
            child.removePropertyChangeListener(this);
        }

        this.treeSelected = null;  // Need to recompute tree selected field
        this.firePropertyChange(AVKey.TREE_NODE, null, this);
    }

    /** {@inheritDoc} */
    @Override
	public TreePath getPath()
    {
        TreePath path = new TreePath();

        TreeNode node = this;
        while (node != null)
        {
            path.add(0, node.getText());
            node = node.getParent();
        }

        return path;
    }

    /** {@inheritDoc} */
    @Override
    public void propertyChange(PropertyChangeEvent propertyChangeEvent)
    {
        this.treeSelected = null;  // Need to recompute tree selected field
        super.propertyChange(propertyChangeEvent);
    }
}
