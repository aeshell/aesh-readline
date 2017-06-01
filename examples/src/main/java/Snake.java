/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.aesh.readline.tty.terminal.TerminalConnection;
import org.aesh.terminal.Attributes;
import org.aesh.terminal.Connection;
import org.aesh.terminal.tty.Point;
import org.aesh.terminal.tty.Size;
import org.aesh.utils.ANSI;
import org.aesh.util.LoggerUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Random;
import java.util.function.Consumer;

/**
 * @author <a href=mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class Snake implements Consumer<Connection> {

    private boolean interrupted = false;
    private Direction direction;
    private Size size;
    private LinkedList<Point> snake = new LinkedList<>();
    private Point food;

    private int score = 0;
    private int sleepTime = 120;
    private boolean acceptingDirectionChanges = true;
    private Attributes attributes;

    @Override
    public void accept(Connection connection) {
        LoggerUtil.doLog();
        connection.openNonBlocking();
        reset(connection);
    }

   private void run(Connection conn) throws InterruptedException {
        while(!interrupted) {
            StringBuilder builder = new StringBuilder();
            Point next = getNextPoint();
            acceptingDirectionChanges = true;

            if (next.x() < 1 || next.x() >= size.getWidth()-1 || next.y() < 2 ||
                    next.y() >= size.getHeight()-1 || snake.contains(next)) {
                displayEnd(conn);
                return;
            }
            else {
                snake.addFirst(next);
                if (next.equals(food)) {
                    score += 5;
                    if(sleepTime > 45)
                        sleepTime -= 3;
                    food = getNewFood(size.getWidth(), size.getHeight());
                    while (snake.contains(food))
                        food = getNewFood(size.getWidth(), size.getHeight());
                }
                else {
                    Point removed = snake.removeLast();
                    builder.append("\033[").append(removed.y() + 1).append(";").append(removed.x() + 1).append("H").append(' ');
                }

                for (Point point : snake) {
                    builder.append("\033[").append(point.y() + 1).append(";").append(point.x() + 1).append("H").append('0');
                }

                builder.append("\033[").append(food.y() + 1).append(";").append(food.x() + 1).append("H").append('x');

                conn.write(builder.toString());
                printTitleAndScore(conn, size.getWidth());

                Thread.sleep(sleepTime);
            }
        }

    }

    private void displayEnd(Connection conn) throws InterruptedException {
        conn.write( "\033[" + size.getHeight() / 2 + ";" + size.getWidth() / 2 + "H" + "YOU LOST!");
        Thread.sleep(3000);
        end(conn);
    }

    private Point getNewFood(int width, int height) {
        return new Point(new Random().nextInt(width-2)+1, new Random().nextInt(height-3)+2);
    }

    private void end(Connection conn) {
        interrupted = true;
        conn.write(ANSI.MAIN_BUFFER);
        conn.write(ANSI.CURSOR_SHOW);
        conn.setAttributes(attributes);
    }

    private void reset(Connection conn) {
        interrupted = true;
        sleepTime = 120;
        snake.clear();
        setup(conn);
        interrupted = false;
        setup(conn);
        try {
            run(conn);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void setup(Connection conn) {
        size = conn.size();
        if(size.getHeight() > 30)
            size = new Size(size.getWidth(), 30);
        else if(size.getWidth() > 80)
            size = new Size(80, size.getHeight());

        // Ctrl-C ends the game
        conn.setSignalHandler(event -> {
            switch (event) {
                case INT:
                    end(conn);
                    break;
            }
        });
        // Keyboard handling
        conn.setStdinHandler(keys -> {
            System.out.println("got: "+Arrays.toString(keys));
            if (keys.length == 3) {
                if (keys[0] == 27 && keys[1] == '[') {
                    switch (keys[2]) {
                        case 'A':
                            if(direction != Direction.DOWN && acceptingDirectionChanges) {
                                direction = Direction.UP;
                                acceptingDirectionChanges = false;
                            }
                            break;
                        case 'B':
                            if(direction != Direction.UP && acceptingDirectionChanges) {
                                direction = Direction.DOWN;
                                acceptingDirectionChanges = false;
                            }
                            break;
                        case 'C':
                            if(direction != Direction.LEFT && acceptingDirectionChanges) {
                                direction = Direction.RIGHT;
                                acceptingDirectionChanges = false;
                            }
                            break;
                        case 'D':
                            if(direction != Direction.RIGHT && acceptingDirectionChanges) {
                                direction = Direction.LEFT;
                                acceptingDirectionChanges = false;
                            }
                            break;
                    }
                }
            }
            else if(keys.length == 1 && keys[0] == 'q')
                end(conn);
        });

        conn.setCloseHandler(close -> end(conn));
        conn.setSizeHandler(size -> reset(conn));

         //switch to alternate buffer
        conn.write(ANSI.ALTERNATE_BUFFER);
        conn.write(ANSI.CURSOR_HIDE);

        attributes = conn.enterRawMode();

        printTitleAndScore(conn, size.getWidth());
        printFrame(conn, size.getWidth(), size.getHeight());
        direction = Direction.RIGHT;
        snake.addFirst(new Point(2,2));
        snake.addFirst(new Point(3,3));
        snake.addFirst(new Point(4,4));
        direction = Direction.DOWN;

        food = getNewFood(size.getWidth(), size.getHeight());
        while(snake.contains(food))
            food = getNewFood(size.getWidth(), size.getHeight());
    }

    private Point getNextPoint() {
        Point curr = snake.peekFirst();
        if (direction == Direction.RIGHT)
            return new Point(curr.x() + 1, curr.y());
        else if (direction == Direction.LEFT)
            return new Point(curr.x() - 1, curr.y());
        else if (direction == Direction.UP)
            return new Point(curr.x(), curr.y() - 1);
        else
            return new Point(curr.x(), curr.y() + 1);
    }

    private void printTitleAndScore(Connection conn, int width) {
        StringBuilder builder = new StringBuilder();
        int mid = width/2-8;
        if(mid < 0)
            mid = 0;
        builder.append("\033[").append(1).append(";").append(mid).append("H")
                .append("SNAKE, score: ").append(score);

        conn.write(builder.toString());
    }

    private void printFrame(Connection conn, int width, int height) {
        StringBuilder builder = new StringBuilder();

        //move to 2,0
        builder.append("\033[").append(2).append(";").append(0).append("H");
        char[] top = new char[width];
        Arrays.fill(top, '-');
        top[0] = '/';
        top[top.length-1] = '\\';
        builder.append( top);
        for(int i=3; i < height; i++) {
            builder.append("\033[").append(i).append(";").append(0).append("H").append('|');
            builder.append("\033[").append(i).append(";").append(width).append("H").append('|');
        }
        top[0] = '\\';
        top[top.length-1] = '/';
        builder.append( top);

        conn.write(builder.toString());
    }

  enum Direction {
      UP('A'),DOWN('B'),RIGHT('C'),LEFT('D');

      private final char ansi;

      Direction(char ansi) {
          this.ansi = ansi;
      }
      char ansi() {
          return ansi;
      }
  }

    public static void main(String[] args) throws IOException {
        new TerminalConnection(new Snake());
    }
}
