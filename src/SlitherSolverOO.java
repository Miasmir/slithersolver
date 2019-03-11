//some different perspectives:
//a vertex with one incident edge needs another
//an edge can't connect two edges with the same loop ID (unless it's the last one)
//a cell must match its clues

import java.util.*;
import java.io.StringReader;
import java.io.PrintStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;

public class SlitherSolverOO
{
	//Some notes on indices!
	//a cell's neighboring vertices:
	//row,col	row,col+1
	//row+1,col	row+1,col+1
	//a cell's neighboring edges:
	//		row*2,col
	//row*2+1,col	row*2+1,col+1
	//		row*2+2,col
	
	//a vertex's neighboring cells:
	//row-1,col-1	row-1,col
	//row,col-1		row,col
	//a vertex's neighboring edges:
	//		row*2-1,col
	//row*2,col-1	row*2,col
	//		row*2+1,col
	
	//a vertical edge's neighboring cells:
	//row/2,col-1	row/2,col
	//a vertical edge's neighboring vertices:
	//row/2,col
	//row/2+1,col
	
	//a horizontal edge's neighboring cells:
	//row/2-1,col
	//row/2,col
	//a horizontal edge's neighboring vertices:
	//row/2,col		row/2,col+1
	
	//for a cell
	static final byte NO_INFO = 5; //as opposed to a clue, 0-4
	//neighboring edge indices
	static final byte EDGE_UP = 0;
	static final byte EDGE_LEFT = 1;
	static final byte EDGE_RIGHT = 2;
	static final byte EDGE_DOWN = 3;
	static final byte EDGE_NONE = 4;
	//neighboring vertex indices
	static final byte VERT_UL = 0;
	static final byte VERT_UR = 1;
	static final byte VERT_LD = 2;
	static final byte VERT_RD = 3;
	static final byte VERT_UP = 4;
	static final byte VERT_LEFT = 5;
	static final byte VERT_RIGHT = 6;
	static final byte VERT_DOWN = 7;
	
	//edge constants; 2 or more is a color, that is, loop ID
	static final int UNKNOWN = 0;
	static final int OFF = 1;
	
	//vertex bitmasks: is the combination possible?
	static final byte UL =		(byte) 1 << 0;
	static final byte UR =		(byte) 1 << 1;
	static final byte UD =		(byte) 1 << 2;
	static final byte LR =		(byte) 1 << 3;
	static final byte LD =		(byte) 1 << 4;
	static final byte RD =		(byte) 1 << 5;
	static final byte UNUSED =	(byte) 1 << 6;
	static final byte ALL =		(byte) ((1 << 7) - 1);
	
	int rows, cols;
	Cell[][] cells;
	HashSet<Cell> interesting;
	LinkedList<Cell> queue;
	Edge[][] edges;
	Vertex[][] vertices;
	int nextColor;
	
	public SlitherSolverOO(String inSource)
	{
		this(new StringReader(inSource));
	}
	public SlitherSolverOO(InputStream inSource)
	{
		this(new InputStreamReader(inSource));
	}
	public SlitherSolverOO(Readable inSource)
	{
		Scanner in = new Scanner(inSource);
		rows = in.nextInt();
		cols = in.nextInt();
		init();
		parse(in);
	}
	
	public SlitherSolverOO(int r, int c)
	{
		rows = r;
		cols = c;
		init();
	}

	//assuming rows and cols have already been set, finish constructing
	public void init()
	{
		interesting = new HashSet<Cell>();
		queue = new LinkedList<Cell>();
		
		cells = new Cell[rows][cols];
		edges = new Edge[rows*2+1][cols+1];
		vertices = new Vertex[rows+1][cols+1];

		nextColor = 2;
		
		for (int r = 0; r < rows+1; r++)
		{
			for (int c = 0; c < cols+1; c++)
			{
				vertices[r][c] = new Vertex(r,c);
			}
		}
		for (int r = 0; r < rows*2+1; r++)
		{
			for (int c = 0; c < cols+1; c++)
			{
				edges[r][c] = new Edge(r,c);
			}
		}
		for (int r = 0; r < rows; r++)
		{
			for (int c = 0; c < cols; c++)
			{
				cells[r][c] = new Cell(r,c);
			}
		}
	}
	
	public void parse(Readable inSource)
	{
		Scanner in = new Scanner(inSource);
		in.nextInt(); //go past the row and column integers
		in.nextInt();
		parse(in);
		in.close();
	}
	
	public void parse(Scanner in)
	{
		in.useDelimiter("");
		
		char look;
		int position = 0;
		while (position < rows*cols)
		{
			if (!in.hasNext())
				System.err.println("not enough input (crashing)");
			look = in.next().charAt(0);
			if (Character.isWhitespace(look)) continue;
			if (look == '.')
				cells[position/cols][position%cols].clue = NO_INFO;
			else
			{
				cells[position/cols][position%cols].clue = (byte)(look-'0');
				interesting.add(cells[position/cols][position%cols]);
			}
			position++;
		}
		
		enqueueNext();
	}
	public static String stripHTML(InputStream inSource)
	{
		try {
			StringBuilder out = new StringBuilder();
			int bytes = inSource.available();
			while (bytes > 0)
			{
				for (;bytes > 0; bytes--) out.append((char)(inSource.read()));
				bytes = inSource.available();
			}
			return stripHTML(out.toString());
		} catch (IOException e) {
			return null;
		}
	}
	public static String stripHTML(String inSource)
	{
		StringBuilder out = new StringBuilder();
		Scanner in = new Scanner(inSource);
		
		HashSet<Integer> xPositions = new HashSet<Integer>();
		HashSet<Integer> yPositions = new HashSet<Integer>();
		int cells = 0;
		int rows = 0;
		int cols = 0;
		
		if (in.findWithinHorizon("class=\"board-back\"",0) == null)
		{
			in.close();
			return "stripHTML: malformed input";
		}

		in.useDelimiter("<");
		in.next();
		while (in.hasNext())
		{
			String token = in.next();
			if (token.contains("class=\"loop-line")) break;
			if (token.contains("class=\"loop-task-cell\""))
			{
				cells++;
			}
			out.append('<');
			out.append(token);
		}
		inSource = out.toString();
		out.setLength(0);
		in.close();
		in = new Scanner(inSource);
	
		//System.out.print("ok");
		
		while(in.hasNext())
		{
			String matchTop = in.findWithinHorizon("top:\\s*\\d+px",0);
			String matchLeft = in.findWithinHorizon("left:\\s*\\d+px",0);
			String matchHint = in.findWithinHorizon(">\\d?</div>",0);
			if (matchTop == null || matchLeft == null || matchHint == null) break;
			int ypos = Integer.parseInt(matchTop.replaceAll("\\D+",""));
			int xpos = Integer.parseInt(matchLeft.replaceAll("\\D+",""));
			if (yPositions.add(ypos))
			{
				rows++;
				out.append('\n');
			}
			if (xPositions.add(xpos))
			{
				cols++;
			}
			
			String digit = matchHint.replaceAll("\\D+","");
			if (digit.length() == 0)
				out.append('.');
			else
				out.append(digit.charAt(0));
		}
		in.close();
		// int cells = out.length();
		// int rows = 0;
		// for (int i = 0; i < inSource.length()-3; i++)
			// if (inSource.regionMatches(i,"<tr",0,3))
				// rows++;
		// rows = rows/2;
		// int cols = cells/rows;
		String result = String.format("%d %d%s",rows,cols,out.toString());
		System.err.println(result);
		
		return result;
	}
	
	public static void main(String[] args)
	{
		SlitherSolverOO s;
		if (args.length > 0)
			s = new SlitherSolverOO(stripHTML(System.in));
		else
			s = new SlitherSolverOO(System.in);
		
		//s.print(false);
		
		s.solve();
		
		s.print(false);
		//s.dumpVertices();
		
	}
	public void solve()
	{
		int iters = 0;
		//int maxWithoutProgress = rows*cols;
		while (!queue.isEmpty())
		{
			Cell c = queue.poll();
			if (c.done) continue;
 			if (iters % 1000 == 0)
			{
				print();
				System.out.printf("Considering %d,%d\n",c.row,c.col);
				System.out.printf("Queue size: %d\n",queue.size());
				// for (Cell ce : queue)
					// System.out.printf("%d,%d; ",ce.row,ce.col);
				System.out.println();
				System.out.println("===================");				
			}   
			iters++;
			
			if (c.clue == 0x00)
			{
				for (Edge e : c.es)
					e.state = OFF;
				c.done = true;
				//System.err.printf("Done: %d,%d (%d)\n",c.row,c.col,c.clue);
				updateVertices(c.row,c.col);
			}
			else
			{	
				//if(c.row == 18 && c.col == 22)
				//	dumpVertices();
				updateVertices(c.row,c.col);
				while (stayOpen(c))
					updateVertices(c.row,c.col);
				if (c.clue == 0x01)
				{
					resolveOneCell(c);
				}
				else if (c.clue == 0x02)
				{
					resolveTwoCell(c);
				}
				else if (c.clue == 0x03)
				{
					resolveThreeCell(c);
				}
				else if (c.clue == 0x04)
				{
					//impossible
				}
				else if (c.clue == NO_INFO)
				{
					int knownEdges = 0;
					for (Edge e : c.es)
						if (e.state != UNKNOWN) knownEdges++;
					if (knownEdges == 4)
					{
						//updateVertices(c.row,c.col); //get those pesky corners
						c.done = true;
						//System.err.printf("Done: %d,%d ( )\n",c.row,c.col);
					}
				}
				//if(c.row == 18 && c.col == 22)
				//	dumpVertices();
			}
/*   			if (edges[6][3] > OFF)
			{
				print();
				dumpVertices();
				System.out.printf("%d,%d\n",row,col);
				rows = 0/0;
			}  */
			/* if(!c.done)
				cellBecomesInteresting(c.row,c.col); */
			enqueueNext();
				
		}	
		System.out.println(iters);
	}

	public void enqueueNext()
	{
		for (Cell c : queue)
		{
			interesting.remove(c);
		}
		queue.addAll(interesting);
		interesting.clear();
	}
	public boolean fillCellQuota(Cell c)
	{
		int offEdges = 0;
		for (Edge e : c.es)
			if (e.state == OFF) offEdges++;
		if (offEdges == 4-c.clue)
		{
			for (Edge e : c.es)
				if (e.state == UNKNOWN) 
					addEdge(e);
			return true;
		}
		return false;
	}
	public boolean stayOpen(Cell c)
	{
		boolean changed = false;
		for (Edge e : c.es)
		{
			if (e.state == UNKNOWN)
			{
				int color1 = e.get(VERT_UL).color;
				int color2 = e.get(VERT_RD).color;
				if (color1 > 0 && color1 == color2)
				{
					e.state = OFF;
					changed = true;
					vertexBecomesInteresting(e.get(VERT_UL),c);
					vertexBecomesInteresting(e.get(VERT_RD),c);
				}
			}
		}
		return changed;
	}
	public void resolveOneCell(Cell c)
	{
		Edge onEdge = null;
		for (Edge e : c.es)
			if (e.state > OFF) onEdge = e;

		if (onEdge != null)
		{
			for (Edge e : c.es)
				if (onEdge != e)
					removeEdge(e);
			c.done = true;
			//System.err.printf("Done: %d,%d (%d)\n",c.row,c.col,c.clue);
			updateVertices(c.row,c.col);
		}
		else
		{
			byte[] vstates = new byte[4];
			boolean changed = true;
			
			while (changed)
			{
				changed = false;
				for (byte vin = VERT_UL; vin <= VERT_RD; vin++)
					vstates[vin] = c.vs[vin].state;
				
				//receive signal
				if ((c.vertex(VERT_UL).state&(UL|UNUSED))==0)
				{
					removeEdge(c.edge(EDGE_RIGHT));
					removeEdge(c.edge(EDGE_DOWN));
				}
				if ((c.vertex(VERT_UR).state&(UR|UNUSED))==0)
				{
					removeEdge(c.edge(EDGE_LEFT));
					removeEdge(c.edge(EDGE_DOWN));
				}
				if ((c.vertex(VERT_LD).state&(LD|UNUSED))==0)
				{
					removeEdge(c.edge(EDGE_RIGHT));
					removeEdge(c.edge(EDGE_UP));
				}
				if ((c.vertex(VERT_RD).state&(RD|UNUSED))==0)
				{
					removeEdge(c.edge(EDGE_LEFT));
					removeEdge(c.edge(EDGE_UP));
				}
				//require a tail
				if (c.edge(EDGE_UP).state == OFF && c.edge(EDGE_LEFT).state == OFF)
				{
					c.vertex(VERT_RD).update(ALL - (UL | RD | UNUSED),true,c);
				}
				if (c.edge(EDGE_UP).state == OFF && c.edge(EDGE_RIGHT).state == OFF)
				{
					c.vertex(VERT_LD).update(ALL - (UR | LD | UNUSED),true,c);
				}
				if (c.edge(EDGE_DOWN).state == OFF && c.edge(EDGE_LEFT).state == OFF)
				{
					c.vertex(VERT_UR).update(ALL - (LD | UR | UNUSED),true,c);
				}
				if (c.edge(EDGE_DOWN).state == OFF && c.edge(EDGE_RIGHT).state == OFF)
				{
					c.vertex(VERT_UL).update(ALL - (UL | RD | UNUSED),true,c);
				}
				
				c.vertex(VERT_UL).update(ALL - RD,true,c);
				c.vertex(VERT_UR).update(ALL - LD,true,c);
				c.vertex(VERT_LD).update(ALL - UR,true,c);
				c.vertex(VERT_RD).update(ALL - UL,true,c);
				updateVertices(c.row,c.col);
				
				for (byte vin = VERT_UL; vin <= VERT_RD; vin++)
				{
					if (vstates[vin] != c.vs[vin].state)
						changed = true;
				}
			}//while(changed)
			
			if (fillCellQuota(c))
			{
				c.done = true;
				//System.err.printf("Done: %d,%d (%d)\n",c.row,c.col,c.clue);
				updateVertices(c.row,c.col);
			}
		}
		//cellBecomesInteresting(c.row,c.col);
	}
	public void resolveTwoCell(Cell c)
	{
		//System.err.printf("Processing the two at %d,%d...\n",row,col);
		int onEdges = 0;
		for (Edge e : c.es)
			if (e.state > OFF) onEdges++;				
		
		if (onEdges == 2)
		{
			for (Edge e : c.es)
				if (e.state == UNKNOWN)
					removeEdge(e);
			c.done = true;
			//System.err.printf("Done: %d,%d (%d)\n",c.row,c.col,c.clue);
			updateVertices(c.row,c.col);
			return;
		}
		else
		{
			byte[] vstates = new byte[4];
			boolean changed = true;
			
			while (changed)
			{
				changed = false;
				for (byte vin = VERT_UL; vin <= VERT_RD; vin++)
					vstates[vin] = c.vs[vin].state;

				//propagate that signal
				byte rdMask = ALL;
				if ((c.vertex(VERT_UL).state & RD) == 0)
				{
					rdMask &= ALL - RD;
				}
				if ((c.vertex(VERT_UL).state & (UL | UNUSED)) == 0)
				{
					rdMask &= ALL - UL;
				}
				if ((c.vertex(VERT_UL).state & (RD | UL | UNUSED)) == 0)
				{
					rdMask &= ALL - UNUSED;
				}
				if (rdMask != ALL)
				{
					c.vertex(VERT_RD).update(rdMask,true,c);
				}
				
				byte ldMask = ALL;
				if ((c.vertex(VERT_UR).state & LD) == 0)
				{
					ldMask &= ALL - LD;
				}
				if ((c.vertex(VERT_UR).state & (UR | UNUSED)) == 0)
				{
					ldMask &= ALL - UR;
				}
				if ((c.vertex(VERT_UR).state & (LD | UR | UNUSED)) == 0)
				{
					ldMask &= ALL - UNUSED;
				}
				if (ldMask != ALL)
				{
					c.vertex(VERT_LD).update(ldMask,true,c);
				}
				
				byte urMask = ALL;
				if ((c.vertex(VERT_LD).state & UR) == 0)
				{
					urMask &= ALL - UR;
				}
				if ((c.vertex(VERT_LD).state & (LD | UNUSED)) == 0)
				{
					urMask &= ALL - LD;
				}
				if ((c.vertex(VERT_LD).state & (UR | LD | UNUSED)) == 0)
				{
					urMask &= ALL - UNUSED;
				}
				if (urMask != ALL)
				{
					c.vertex(VERT_UR).update(urMask,true,c);
				}
				
				byte ulMask = ALL;
				if ((c.vertex(VERT_RD).state & UL) == 0)
				{
					ulMask &= ALL - UL;
				}
				if ((c.vertex(VERT_RD).state & (RD | UNUSED)) == 0)
				{
					ulMask &= ALL - RD;
				}
				if ((c.vertex(VERT_RD).state & (UL | RD | UNUSED)) == 0)
				{
					ulMask &= ALL - UNUSED;
				}
				if (ulMask != ALL)
				{
					c.vertex(VERT_UL).update(ulMask,true,c);
				}
				
				//the both-or-neither case
				if (c.vertex(VERT_UL).state == (RD | UNUSED))
				{
					c.vertex(VERT_RD).update(RD | UL | UNUSED,true,c);
					c.vertex(VERT_UR).update(ALL - (UR | LD | UNUSED),true,c);
					c.vertex(VERT_LD).update(ALL - (UR | LD | UNUSED),true,c);
					//verticesBecomeInteresting(c.row,c.col);
				}
				if (c.vertex(VERT_UR).state == (LD | UNUSED))
				{
					c.vertex(VERT_LD).update(LD | UR | UNUSED,true,c);
					c.vertex(VERT_UL).update(ALL - (UL | RD | UNUSED),true,c);
					c.vertex(VERT_RD).update(ALL - (UL | RD | UNUSED),true,c);
					//verticesBecomeInteresting(c.row,c.col);
				}
				if (c.vertex(VERT_LD).state == (UR | UNUSED))
				{
					c.vertex(VERT_UR).update(UR | LD | UNUSED,true,c);
					c.vertex(VERT_UL).update(ALL - (UL | RD | UNUSED),true,c);
					c.vertex(VERT_RD).update(ALL - (UL | RD | UNUSED),true,c);
					//verticesBecomeInteresting(c.row,c.col);
				}
				if (c.vertex(VERT_RD).state == (UL | UNUSED))
				{
					c.vertex(VERT_UL).update(UL | RD | UNUSED,true,c);
					c.vertex(VERT_UR).update(ALL - (UR | LD | UNUSED),true,c);
					c.vertex(VERT_LD).update(ALL - (UR | LD | UNUSED),true,c);
					//verticesBecomeInteresting(c.row,c.col);
				}
				//process of elimination
				//also: prevent poking us with a corner when we have nowhere to go
				if (c.edge(EDGE_UP).state == OFF)
				{
					if ((c.vertex(VERT_LD).state&UR)==0)
					{
						addEdge(c.edge(EDGE_RIGHT));
					}
					if ((c.vertex(VERT_RD).state&UL)==0)
					{
						addEdge(c.edge(EDGE_LEFT));
					}
					c.vertex(VERT_LD).update(ALL-(LD|UNUSED),true,c);
					c.vertex(VERT_RD).update(ALL-(RD|UNUSED),true,c);
				}
				if (c.edge(EDGE_LEFT).state == OFF)
				{
					if ((c.vertex(VERT_UR).state&LD)==0)
					{
						addEdge(c.edge(EDGE_DOWN));
					}
					if ((c.vertex(VERT_RD).state&UL)==0)
					{
						addEdge(c.edge(EDGE_UP));
					}
					c.vertex(VERT_UR).update(ALL-(UR|UNUSED),true,c);
					c.vertex(VERT_RD).update(ALL-(RD|UNUSED),true,c);
				}
				if (c.edge(EDGE_RIGHT).state == OFF)
				{
					if ((c.vertex(VERT_UL).state&RD)==0)
					{
						addEdge(c.edge(EDGE_DOWN));
					}
					if ((c.vertex(VERT_LD).state&UR)==0)
					{
						addEdge(c.edge(EDGE_UP));
					}
					c.vertex(VERT_UL).update(ALL-(UL|UNUSED),true,c);
					c.vertex(VERT_LD).update(ALL-(LD|UNUSED),true,c);
				}
				if (c.edge(EDGE_DOWN).state == OFF)
				{
					if ((c.vertex(VERT_UL).state&RD)==0)
					{
						addEdge(c.edge(EDGE_RIGHT));
					}
					if ((c.vertex(VERT_UR).state&LD)==0)
					{
						addEdge(c.edge(EDGE_LEFT));
					}
					c.vertex(VERT_UL).update(ALL-(UL|UNUSED),true,c);
					c.vertex(VERT_UR).update(ALL-(UR|UNUSED),true,c);
				}
				//on edge adjacent to off edge
				if (c.edge(EDGE_UP).state > OFF && c.edge(EDGE_LEFT).state == OFF ||
					c.edge(EDGE_UP).state == OFF && c.edge(EDGE_LEFT).state > OFF)
				{
					c.vertex(VERT_RD).update(ALL - (UL | RD | UNUSED),true,c);	
				}
				if (c.edge(EDGE_UP).state > OFF && c.edge(EDGE_RIGHT).state == OFF ||
					c.edge(EDGE_UP).state == OFF && c.edge(EDGE_RIGHT).state > OFF)
				{
					c.vertex(VERT_LD).update(ALL - (UR | LD | UNUSED),true,c);
				}
				if (c.edge(EDGE_DOWN).state > OFF && c.edge(EDGE_LEFT).state == OFF ||
					c.edge(EDGE_DOWN).state == OFF && c.edge(EDGE_LEFT).state > OFF)
				{
					c.vertex(VERT_UR).update(ALL - (UR | LD | UNUSED),true,c);	
				}
				if (c.edge(EDGE_DOWN).state > OFF && c.edge(EDGE_RIGHT).state == OFF ||
					c.edge(EDGE_DOWN).state == OFF && c.edge(EDGE_RIGHT).state > OFF)
				{
					c.vertex(VERT_UL).update(ALL - (UL | RD | UNUSED),true,c);
				}

				updateVertices(c.row,c.col);
				
				for (byte vin = VERT_UL; vin <= VERT_RD; vin++)
					if (vstates[vin] != c.vs[vin].state)
						changed = true;
			} //while(changed)
			
			if (fillCellQuota(c))
			{
				c.done = true;
				//System.err.printf("Done: %d,%d (%d)\n",c.row,c.col,c.clue);
				updateVertices(c.row,c.col);
			}
		}
		//cellBecomesInteresting(c.row,c.col);
		//System.err.printf("Done processing the two at %d,%d.\n",row,col);
	}
	public void resolveThreeCell(Cell c)
	{
		int onEdges = 0;
		for (Edge e : c.es)
			if (e.state > OFF) onEdges++;
		
		if (onEdges == 3)
		{
			for (Edge e : c.es)
				if (e.state == UNKNOWN)
					removeEdge(e);
			c.done = true;
			//System.err.printf("Done: %d,%d (%d)\n",c.row,c.col,c.clue);
			updateVertices(c.row,c.col);
		}
		else
		{
			byte[] vstates = new byte[4];
			boolean changed = true;
			
			while (changed)
			{
				changed = false;
				for (byte vin = VERT_UL; vin <= VERT_RD; vin++)
					vstates[vin] = c.vs[vin].state;

				//pigeonhole principle
				if ((c.vertex(VERT_UL).state&RD) == 0)
				{
					addEdge(c.edge(EDGE_RIGHT));
					addEdge(c.edge(EDGE_DOWN));
				}
				if ((c.vertex(VERT_UR).state&LD) == 0)
				{
					addEdge(c.edge(EDGE_LEFT));
					addEdge(c.edge(EDGE_DOWN));
				}
				if ((c.vertex(VERT_LD).state&UR) == 0)
				{
					addEdge(c.edge(EDGE_UP));
					addEdge(c.edge(EDGE_RIGHT));
				}
				if ((c.vertex(VERT_RD).state&UL) == 0)
				{
					addEdge(c.edge(EDGE_UP));
					addEdge(c.edge(EDGE_LEFT));
				}
				
				//send a signal
				if (c.vertex(VERT_UL).state == RD)
				{
					c.vertex(VERT_RD).update(ALL-UL,true,c);
				}
				if (c.vertex(VERT_UR).state == LD)
				{
					c.vertex(VERT_LD).update(ALL-UR,true,c);
				}
				if (c.vertex(VERT_LD).state == UR)
				{
					c.vertex(VERT_UR).update(ALL-LD,true,c);
				}
				if (c.vertex(VERT_RD).state == UL)
				{
					c.vertex(VERT_UL).update(ALL-RD,true,c);
				}
				
				//adjacent threes
				if (c.row > 0 && cells[c.row-1][c.col].clue == 3)
				{
					addEdge(c.edge(EDGE_UP));
					addEdge(c.edge(EDGE_DOWN));
					c.vertex(VERT_UL).update(UR | RD,true,c);
					c.vertex(VERT_UR).update(UL | LD,true,c);
				}
				if (c.row < rows-1 && cells[c.row+1][c.col].clue == 3)
				{
					addEdge(c.edge(EDGE_UP));
					addEdge(c.edge(EDGE_DOWN));
					c.vertex(VERT_LD).update(UR | RD,true,c);
					c.vertex(VERT_RD).update(UL | LD,true,c);
				}
				if (c.col > 0 && cells[c.row][c.col-1].clue == 3)
				{
					addEdge(c.edge(EDGE_LEFT));
					addEdge(c.edge(EDGE_RIGHT));
					c.vertex(VERT_UL).update(LD | RD,true,c);
					c.vertex(VERT_LD).update(UL | UR,true,c);
				}
				if (c.col < cols-1 && cells[c.row][c.col+1].clue == 3)
				{
					addEdge(c.edge(EDGE_LEFT));
					addEdge(c.edge(EDGE_RIGHT));
					c.vertex(VERT_UR).update(LD | RD,true,c);
					c.vertex(VERT_RD).update(UL | UR,true,c);
				}
				
				//loop closing stuff
/*   				int foundColor = 0;
				for (Vertex v : c.vs)
					if (v.color > 0)
						foundColor = v.color;
				if (foundColor > 0)
					for (Vertex v : c.vs)
						if (v.color > 0 && foundColor != v.color)
							floodColor(v,foundColor); */
				
				
/* 				if (c.row > 0)
				{
					int color1 = c.vertex(VERT_UL).getVertex(EDGE_UP).color;
					int color2 = c.vertex(VERT_UR).getVertex(EDGE_UP).color;
					if (color1 > 0 && color1 == colors[VERT_UR])
						c.vertex(VERT_UL).get(EDGE_UP).turnOff();
					if (color2 > 0 && color2 == colors[VERT_UL])
						c.vertex(VERT_UR).get(EDGE_UP).turnOff();
				}
				if (c.col > 0)
				{
					int color1 = c.vertex(VERT_UL).getVertex(EDGE_LEFT).color;
					int color2 = c.vertex(VERT_LD).getVertex(EDGE_LEFT).color;
					if (color1 > 0 && color1 == colors[VERT_LD])
						c.vertex(VERT_UL).get(EDGE_LEFT).turnOff();
					if (color2 > 0 && color2 == colors[VERT_UL])
						c.vertex(VERT_LD).get(EDGE_LEFT).turnOff();
				}
				if (c.col < cols-1)
				{
					int color1 = c.vertex(VERT_UR).getVertex(EDGE_RIGHT).color;
					int color2 = c.vertex(VERT_RD).getVertex(EDGE_RIGHT).color;
					if (color1 > 0 && color1 == colors[VERT_RD])
						c.vertex(VERT_UR).get(EDGE_RIGHT).turnOff();
					if (color2 > 0 && color2 == colors[VERT_UR])
						c.vertex(VERT_RD).get(EDGE_RIGHT).turnOff();
				}
				if (c.row < rows-1)
				{
					int color1 = c.vertex(VERT_LD).getVertex(EDGE_DOWN).color;
					int color2 = c.vertex(VERT_RD).getVertex(EDGE_DOWN).color;
					if (color1 > 0 && color1 == colors[VERT_RD])
						c.vertex(VERT_LD).get(EDGE_DOWN).turnOff();
					if (color2 > 0 && color2 == colors[VERT_LD])
						c.vertex(VERT_RD).get(EDGE_DOWN).turnOff();
				} */
				
				

				c.vertex(VERT_UL).update(ALL - (UL | UNUSED),true,c);
				c.vertex(VERT_UR).update(ALL - (UR | UNUSED),true,c);
				c.vertex(VERT_LD).update(ALL - (LD | UNUSED),true,c);
				c.vertex(VERT_RD).update(ALL - (RD | UNUSED),true,c);
				updateVertices(c.row,c.col);
				
				for (byte vin = VERT_UL; vin <= VERT_RD; vin++)
					if (vstates[vin] != c.vs[vin].state)
						changed = true;
			} //while(changed)
			
			if (fillCellQuota(c))
			{
				c.done = true;
				//System.err.printf("Done: %d,%d (%d)\n",c.row,c.col,c.clue);
				updateVertices(c.row,c.col);
			}
		}
		//cellBecomesInteresting(c.row,c.col);
	}
	public void addEdge(Edge e)
	{
		if (e == null) return;
		if (e.state != UNKNOWN) return;
		//System.err.printf("Adding edge %d,%d\n",row,col);
		////print();
		if (e.horizontal)
		{
			matchColors(e.get(VERT_LEFT),EDGE_RIGHT);
		}
		else
		{
			matchColors(e.get(VERT_UP),EDGE_DOWN);
		}
		vertexBecomesInteresting(e.get(VERT_UL));
		vertexBecomesInteresting(e.get(VERT_RD));
	}
	public void removeEdge(Edge e)
	{
		if (e == null) return;
		if (e.state != UNKNOWN) return;
		e.state = OFF;
		vertexBecomesInteresting(e.get(VERT_UL));
		vertexBecomesInteresting(e.get(VERT_RD));		
	}
	public void updateVertices(int row, int col)
	{
		updateVertex(row,col);
		updateVertex(row,col+1);
		updateVertex(row+1,col);
		updateVertex(row+1,col+1);
	}
	public void updateVertex(int row, int col)
	{
		Vertex vert = vertices[row][col];
		byte v = vert.state;
		
		if (Integer.bitCount(v) == 1)
		{
			boolean foundUnknown = false;
			foundUnknown |= row > 0 && (vert.get(EDGE_UP).state == UNKNOWN);
			foundUnknown |= col > 0 && (vert.get(EDGE_LEFT).state == UNKNOWN);
			foundUnknown |= row < rows && (vert.get(EDGE_DOWN).state == UNKNOWN);
			foundUnknown |= col < cols && (vert.get(EDGE_RIGHT).state == UNKNOWN);
			if (!foundUnknown)
			{
				//System.out.println("we double checked something");
				return;
			}
		}
		
		boolean upOK = false;
		boolean leftOK = false;
		boolean rightOK = false;
		boolean downOK = false;
		
		boolean changed = true;
		while (changed)
		{
			changed = false;
			upOK = (row > 0) && (vert.get(EDGE_UP).state != OFF) && ((v&(UL|UR|UD))>0);
			leftOK = (col > 0) && (vert.get(EDGE_LEFT).state != OFF) && ((v&(UL|LR|LD))>0);
			rightOK = (col < cols) && (vert.get(EDGE_RIGHT).state != OFF) && ((v&(UR|LR|RD))>0);
			downOK = (row < rows) && (vert.get(EDGE_DOWN).state != OFF) && ((v&(UD|LD|RD))>0);
			byte nv = v;
			if (!upOK)
			{
				nv &= ALL - (UL | UR | UD);
			}
			if (!leftOK)
			{
				nv &= ALL - (UL | LR | LD);
			}
			if (!rightOK)
			{
				nv &= ALL - (UR | LR | RD);
			}
			if (!downOK)
			{
				nv &= ALL - (UD | LD | RD);
			}
			if (v != nv)
			{
				//int out = (upOK?1:0)+(leftOK?10:0)+(rightOK?100:0)+(downOK?1000:0);
				//System.err.printf("%d,%d: %d;%d\n",row,col,nv,out);
				v = nv;
				changed = true;
			}
		}
		changed = (vert.state != v);
		vert.state = v;
		boolean done = Integer.bitCount(v) == 1;
		
		if (done)
		{
			//System.err.println("yo!");
			if (upOK)
			{
				if (matchColors(vert,EDGE_UP))
					vertexBecomesInteresting(vert.getVertex(EDGE_UP));
			}
			else if (row > 0)
			{
				if (vert.get(EDGE_UP).state != OFF)
				{
					vert.get(EDGE_UP).state = OFF;
					vertexBecomesInteresting(vert.getVertex(EDGE_UP));
				}
			}
			
			if (leftOK)
			{
				if(matchColors(vert,EDGE_LEFT))
					vertexBecomesInteresting(vert.getVertex(EDGE_LEFT));
			}
			else if (col > 0)
			{
				if (vert.get(EDGE_LEFT).state != OFF)
				{
					vert.get(EDGE_LEFT).state = OFF;
					vertexBecomesInteresting(vert.getVertex(EDGE_LEFT));
				}
			}
			
			if (rightOK)
			{
				if(matchColors(vert,EDGE_RIGHT))
					vertexBecomesInteresting(vert.getVertex(EDGE_RIGHT));
			}
			else if (col < cols)
			{
				if (vert.get(EDGE_RIGHT).state != OFF)
				{
					vert.get(EDGE_RIGHT).state = OFF;
					vertexBecomesInteresting(vert.getVertex(EDGE_RIGHT));
				}
			}

			if (downOK)
			{
				if(matchColors(vert,EDGE_DOWN))
					vertexBecomesInteresting(vert.getVertex(EDGE_DOWN));
			}
			else if (row < rows)
			{
				if (vert.get(EDGE_DOWN).state != OFF)
				{
					vert.get(EDGE_DOWN).state = OFF;
					vertexBecomesInteresting(vert.getVertex(EDGE_DOWN));
				}
			}
			return;
		}
		else //still more than one possibility
		{
			boolean interesting = changed;
			changed = true;
			while(changed)
			{
				changed = false;
				boolean forceUpOn = (v&(LR|LD|RD|UNUSED)) == 0 && (row > 0) && (vert.get(EDGE_UP).state == UNKNOWN);
				boolean forceLeftOn = (v&(UR|UD|RD|UNUSED)) == 0 && (col > 0) && (vert.get(EDGE_LEFT).state == UNKNOWN);
				boolean forceRightOn = (v&(UL|UD|LD|UNUSED)) == 0 && (col < cols) && (vert.get(EDGE_RIGHT).state == UNKNOWN);
				boolean forceDownOn = (v&(UL|UR|LR|UNUSED)) == 0 && (row < rows) && (vert.get(EDGE_DOWN).state == UNKNOWN);
				changed |= forceUpOn|forceLeftOn|forceRightOn|forceDownOn;
				if (forceUpOn)
				{
					if(matchColors(vert,EDGE_UP))
						vertexBecomesInteresting(vert.getVertex(EDGE_UP));
				}
				if (forceLeftOn)
				{
					if(matchColors(vert,EDGE_LEFT))
						vertexBecomesInteresting(vert.getVertex(EDGE_LEFT));
				}
				if (forceRightOn)
				{
					if(matchColors(vert,EDGE_RIGHT))
						vertexBecomesInteresting(vert.getVertex(EDGE_RIGHT));
				}
				if (forceDownOn)
				{
					if(matchColors(vert,EDGE_DOWN))
						vertexBecomesInteresting(vert.getVertex(EDGE_DOWN));
				}

				boolean upOn = (row > 0) && (vert.get(EDGE_UP).state > OFF);
				boolean leftOn = (col > 0) && (vert.get(EDGE_LEFT).state > OFF);
				boolean rightOn = (col < cols) && (vert.get(EDGE_RIGHT).state > OFF);
				boolean downOn = (row < rows) && (vert.get(EDGE_DOWN).state > OFF);
				if (upOn)
				{
					v &= ALL - (LR | LD | RD | UNUSED);
				}
				if (leftOn)
				{
					v &= ALL - (UD | UR | RD | UNUSED);
				}
				if (rightOn)
				{
					v &= ALL - (UL | UD | LD | UNUSED);
				}
				if (downOn)
				{
					v &= ALL - (UL | UR | LR | UNUSED);
				}
				changed |= (vert.state != v);
				vert.state = v;
				if (Integer.bitCount(v) == 1)
				{
					updateVertex(row,col);
					return;
				}
				boolean upOff = (row > 0) && (vert.get(EDGE_UP).state == UNKNOWN) && ((v&(UL|UR|UD))==0);
				boolean leftOff = (col > 0) && (vert.get(EDGE_LEFT).state == UNKNOWN) && ((v&(UL|LR|LD))==0);
				boolean rightOff = (col < cols) && (vert.get(EDGE_RIGHT).state == UNKNOWN) && ((v&(UR|LR|RD))==0);
				boolean downOff = (row < rows) && (vert.get(EDGE_DOWN).state == UNKNOWN) && ((v&(UD|LD|RD))==0);
				if (upOff)
				{
					vert.get(EDGE_UP).state = OFF;
					vertexBecomesInteresting(vert.getVertex(EDGE_UP));
					interesting = true;
				}
				if (leftOff)
				{
					vert.get(EDGE_LEFT).state = OFF;
					vertexBecomesInteresting(vert.getVertex(EDGE_LEFT));
					interesting = true;
				}
				if (rightOff)
				{
					vert.get(EDGE_RIGHT).state = OFF;
					vertexBecomesInteresting(vert.getVertex(EDGE_RIGHT));
					interesting = true;
				}
				if (downOff)
				{
					vert.get(EDGE_DOWN).state = OFF;
					vertexBecomesInteresting(vert.getVertex(EDGE_DOWN));
					interesting = true;
				}
			
				interesting |= changed;
			}
			if (interesting)
				vertexBecomesInteresting(row,col);
/* 			else
			{
				if (upOff || leftOff)
					cellBecomesInteresting(row-1,col-1);
				if (upOff || rightOff)
					cellBecomesInteresting(row-1,col);
				if (downOff || leftOff)
					cellBecomesInteresting(row,col-1);
				if (downOff || rightOff)
					cellBecomesInteresting(row,col);
			} */

		}
	}
	public void floodColor(Vertex v, int newColor)
	{
		boolean foundEdge = true;
		boolean interesting = false;
		while(foundEdge)
		{
			v.color = newColor;
			foundEdge = false;
			for (byte d = EDGE_UP; d <= EDGE_DOWN; d++)
			{
				Edge tempEdge = v.get(d);
				if (tempEdge != null && tempEdge.state > OFF && tempEdge.state != newColor)
				{
					tempEdge.state = newColor;
					v = v.getVertex(d);
					foundEdge = true;
					interesting = true;
					break;
				}
			}
		}
		if (interesting)
			vertexBecomesInteresting(v);
	}
	//returns true iff something was changed
	public boolean matchColors(Vertex v1, byte dir)
	{
		Vertex v2 = v1.getVertex(dir);
		Edge e = v1.get(dir);
		
		//v1.give(dir);
		v2.give(reverse(dir));
		
		int newColor;
		if (v1.color == 0 && v2.color == 0) //first connection for both
		{
			newColor = nextColor++;
			v1.color = newColor;
			v2.color = newColor;
			e.state = newColor;
			return true;
		}
		if (v1.color == v2.color) //already the same, fill in the edge just in case
		{
			if (e.state == v1.color) return false;
			e.state = v1.color;
			return true;
		}
		//if either one is unused, it assumes the color of the other
		if (v1.color == 0)
		{
			newColor = v2.color;
			v1.color = newColor;
			e.state = newColor;
			return true;
		}
		if (v2.color == 0)
		{
			newColor = v1.color;
			v2.color = newColor;
			e.state = newColor;
			return true;
		}
		//if both are used, one color predominates
		newColor = v1.color;
		e.state = newColor;
		//and we have to floodfill the other segment with the new color
		floodColor(v2,newColor);
		
		return true;
	}
	public void verticesBecomeInteresting(int r, int c)
	{
		for (int dr = -1; dr <= 1; dr++)
		{
			for (int dc = -1; dc <= 1; dc++)
			{
				if (dr != 0 || dc != 0)
					cellBecomesInteresting(r+dr,c+dc);
			}
		}
	}
	public void vertexBecomesInteresting(Vertex v, Cell toExclude)
	{
		if (v != null)
		{
			if (toExclude != null)
				vertexBecomesInteresting(v.row,v.col,toExclude.row,toExclude.col);
			else
				vertexBecomesInteresting(v.row,v.col);
		}
	}
	public void vertexBecomesInteresting(Vertex v)
	{
		if (v != null)
			vertexBecomesInteresting(v.row,v.col);
	}
	public void vertexBecomesInteresting(int r, int c)
	{
		for (int dr = -1; dr <= 0; dr++)
		{
			for (int dc = -1; dc <= 0; dc++)
			{
				cellBecomesInteresting(r+dr,c+dc);
			}
		}
	}
	public void vertexBecomesInteresting(int r, int c, int er, int ec)
	{
		for (int dr = -1; dr <= 0; dr++)
		{
			for (int dc = -1; dc <= 0; dc++)
			{
				if (r+dr != er || c+dc != ec)
					cellBecomesInteresting(r+dr,c+dc);
			}
		}
	}
	public void cellBecomesInteresting(int r, int c)
	{
		if (r >= 0 && r < rows && c >= 0 && c < cols && !cells[r][c].done)
			interesting.add(cells[r][c]);
	}
	public void print()
	{
		print(true);
	}
	public void print(boolean showXs)
	{
		//print(showXs,false);
		//System.out.println("\u0303\u0244");
		//System.out.println(System.getProperty("file.encoding"));
		print(showXs,true);
	}
	public void print(boolean showXs, boolean unicode)
	{
		//draw it with Unicode box-drawing characters! why not.
		StringBuilder out = new StringBuilder((rows+1)*(cols+3)*7);
		for (int r = 0; r < rows*2+1; r++)
		{
			for (int c = 0; c < cols*2+1; c++)
			{
				if (r%2 == 0 && c%2 == 0)
				{
					byte v = vertices[r/2][c/2].state;
					if (unicode)
					{
						if (v == UD)
							out.append('\u2502');
						else if (v == UL)
							out.append('\u2518');
						else if (v == UR)
							out.append('\u2514');
						else if (v == LR)
							out.append('\u2500');
						else if (v == LD)
							out.append('\u2510');
						else if (v == RD)
							out.append('\u250c');
						else
							out.append(' ');
					}
					else
					{
						if (v == UD)
							out.append('|');
						else if (v == UL)
							out.append('+');
						else if (v == UR)
							out.append('+');
						else if (v == LR)
							out.append('-');
						else if (v == LD)
							out.append('+');
						else if (v == RD)
							out.append('+');
						else
							out.append(' ');
					}
				}
				else if (r%2 == 0)
				{
					int e = edges[r][c/2].state;
					if (e > OFF)
					{
						if (unicode)
							out.append('\u2500');
						else
							out.append('-');
					}
					else if (e == OFF && showXs)
						out.append('x');
					else
						out.append(' ');
				}
				else if (c%2 == 0)
				{
					int e = edges[r][c/2].state;
					if (e > OFF)
					{
						if (unicode)
							out.append('\u2502');
						else
							out.append('|');
					}
					else if (e == OFF && showXs)
						out.append('x');
					else
						out.append(' ');
				}
				else
				{
					byte cell = cells[r/2][c/2].clue;
					if (cell < NO_INFO)
						out.append((char)('0'+cell));
					else if (showXs)
						out.append('.');
					else
						out.append(' ');
				}
			}
			out.append('\n');
		}
		if (unicode)
		{
			try {
				//(new PrintStream(System.out,true,"UTF-8")).print(out);
				byte[] encoded = out.toString().getBytes("UTF-8");
				System.out.write(encoded,0,encoded.length);
			} catch(Exception e) {
				System.err.println("UTF-8 printing failed");
				System.out.print(out);
			}
		}
		else
		{
			System.out.print(out);
		}
	}
	public void dumpVertices()
	{
		for (int r = 0; r <= rows; r++)
		{
			for (int c = 0; c <= cols; c++)
			{
				System.out.printf("%d,%d: %s\n",r,c,String.format("%7s",Integer.toBinaryString(vertices[r][c].state)).replace(' ','0'));
			}
		}
	}
	public byte reverse(byte dir)
	{
		switch(dir)
		{
			case EDGE_UP:
				return EDGE_DOWN;
			case EDGE_LEFT:
				return EDGE_RIGHT;
			case EDGE_RIGHT:
				return EDGE_LEFT;
			case EDGE_DOWN:
				return EDGE_UP;
			default:
				return 0x00;
		}
	}
	class Vertex
	{
		int row, col;
		byte state;
		int color;
		
		public Vertex(int r, int c)
		{
			row = r;
			col = c;
			byte subtract = 0x00;
			if (r == 0)
			{
				subtract |= (UL | UR | UD);
			}
			else if (r == rows)
			{
				subtract |= (UD | LD | RD);
			}
			if (c == 0)
			{
				subtract |= (UL | LR | LD);
			}
			else if (c == cols)
			{
				subtract |= (UR | LR | RD);
			}
			state |= ALL - subtract;
			color = UNKNOWN;
		}
		public boolean update(int mask)
		{
			return update((byte)mask,false,null);
		}
		public boolean update(int mask, boolean becomeInteresting)
		{
			return update((byte)mask,becomeInteresting,null);
		}
		public boolean update(int mask, boolean becomeInteresting, Cell toExclude)
		{
			return update((byte)mask,becomeInteresting,toExclude);
		}
		// public boolean update(byte mask)
		// {
			// return update(mask,false,null);
		// }
		// public boolean update(byte mask)
		// {
			// return update(mask,false,null);
		// }
		public boolean update(byte mask, boolean becomeInteresting, Cell toExclude)
		{
			byte newState = (byte)(state & mask);
			boolean changed = newState != state;
			state = newState;
			if (changed && becomeInteresting)
				vertexBecomesInteresting(this,toExclude);
			return changed;
		}
		public void give(byte dir)
		{
			switch(dir)
			{
				case EDGE_UP:
					state &= ALL - (UNUSED | LR | LD | RD);
					break;
				case EDGE_LEFT:
					state &= ALL - (UNUSED | UR | UD | RD);
					break;
				case EDGE_RIGHT:
					state &= ALL - (UNUSED | UL | LD | UD);
					break;
				case EDGE_DOWN:
					state &= ALL - (UNUSED | LR | UL | UR);
					break;
				default:
					break;
			}
		}
		
		public Edge get(byte dir)
		{
			switch(dir)
			{
				case EDGE_UP:
					return (row > 0?edges[row*2-1][col]:null);
				case EDGE_LEFT:
					return (col > 0?edges[row*2][col-1]:null);
				case EDGE_RIGHT:
					return (col < cols?edges[row*2][col]:null);
				case EDGE_DOWN:
					return (row < rows?edges[row*2+1][col]:null);
				default:
					return null;
			}
		}
		public int getEdgeState(byte dir)
		{
			Edge e = get(dir);
			if (e == null) return OFF;
			return e.state;
		}
		public void setEdgeState(byte dir, int newState)
		{
			Edge e = get(dir);
			if (e == null) return;
			e.state = newState;
		}
		public Vertex getVertex(byte dir)
		{
			switch(dir)
			{
				case EDGE_UP:
					return (row > 0?edges[row*2-1][col].get(VERT_UP):null);
				case EDGE_LEFT:
					return (col > 0?edges[row*2][col-1].get(VERT_LEFT):null);
				case EDGE_RIGHT:
					return (col < cols?edges[row*2][col].get(VERT_RIGHT):null);
				case EDGE_DOWN:
					return (row < rows?edges[row*2+1][col].get(VERT_DOWN):null);
				default:
					return null;
			}
		}
	}
	class Edge
	{
		int row, col;
		boolean horizontal;
		int state;
		
		public Edge(int r, int c)
		{
			row = r;
			col = c;
			horizontal = row%2==0;
			state = UNKNOWN;
		}
		public Vertex get(byte dir)
		{
			switch (dir)
			{
				case VERT_UP:
					return (!horizontal?vertices[row/2][col]:null);
				case VERT_LEFT:
					return (horizontal?vertices[row/2][col]:null);
				case VERT_RIGHT:
					return (horizontal?vertices[row/2][col+1]:null);
				case VERT_DOWN:
					return (!horizontal?vertices[row/2+1][col]:null);
				case VERT_UL:
					return vertices[row/2][col];
				case VERT_RD:
					return (horizontal?vertices[row/2][col+1]:vertices[row/2+1][col]);
				default:
					return null;
			}
		}
		public boolean turnOff()
		{
			if (state == UNKNOWN)
			{
				state = OFF;
				return true;
			}
			return false;
		}
	}
	class Cell
	{
		int row, col;
		byte clue;
		boolean done;
		Edge[] es;
		Vertex[] vs;
		
		public Cell (int r, int c)
		{
			this(r,c,NO_INFO);
		}
		public Cell (int r, int c, byte clu)
		{
			row = r;
			col = c;
			clue = clu;
			done = false;
			es = new Edge[4];
			vs = new Vertex[4];
			for (byte i = 0; i < 4; i++)
			{
				es[i] = edge(i);
				vs[i] = vertex(i);
			}
		}
		public Edge edge(byte dir)
		{
			switch(dir)
			{
				case EDGE_UP:
					return edges[row*2][col];
				case EDGE_LEFT:
					return edges[row*2+1][col];
				case EDGE_RIGHT:
					return edges[row*2+1][col+1];
				case EDGE_DOWN:
					return edges[row*2+2][col];
				default:
					return null;
			}
		}
		public Vertex vertex(byte dir)
		{
			switch(dir)
			{
				case VERT_UL:
					return vertices[row][col];
				case VERT_UR:
					return vertices[row][col+1];
				case VERT_LD:
					return vertices[row+1][col];
				case VERT_RD:
					return vertices[row+1][col+1];
				default:
					return null;
			}
		}
	}
}