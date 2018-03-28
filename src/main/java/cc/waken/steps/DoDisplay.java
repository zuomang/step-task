package cc.waken.steps;

import cc.waken.config.IStep;
import cc.waken.config.TaskStep;

/**
 * Created by setup on 26/03/2018.
 */
@TaskStep(active = true, step = 2)
public class DoDisplay implements IStep {
    public void run() {
        System.out.println("Do Display run function");
    }
}
