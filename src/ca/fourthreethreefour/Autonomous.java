package ca.fourthreethreefour;

import ca.fourthreethreefour.subsystems.Bucket;
import ca.fourthreethreefour.subsystems.Drive;
import edu.first.command.Command;
import edu.first.commands.CommandGroup;
import edu.first.commands.common.LoopingCommand;
import edu.first.commands.common.WaitCommand;
import edu.first.module.actuators.Drivetrain;
import edu.first.module.actuators.DualActionSolenoid;
import edu.first.module.actuators.DualActionSolenoid.Direction;

public class Autonomous extends CommandGroup implements Drive, Bucket {
    public Autonomous() { //TODO fix this, add switch case for multiple autos (this is center)
        appendSequential(new TimedDrive(drivetrain, 0.2, 0.2, 1000L)); 
        appendSequential(new WaitCommand(1));
        appendConcurrent(new McBukkit(bucketSolenoid));
        appendSequential(new WaitCommand(1));
        appendSequential(new TimedDrive(drivetrain, -0.2, -0.2, 1000L));
        appendSequential(new TimedDrive(drivetrain, 0.4, 0.2, 500L));
        appendSequential(new TimedDrive(drivetrain, 0.2, 0.2, 500L));
        appendSequential(new TimedDrive(drivetrain, 0.2, 0.4, 500L));
        appendSequential(new TimedDrive(drivetrain, 0.2, 0.2, 1000L));
    }

    class TimedDrive extends LoopingCommand {

        private Drivetrain drive;
        private double left, right;
        private long durationMS;
        private long start = 0;

        public TimedDrive(Drivetrain drive, double left, double right, long durationMS) {
            this.drive = drive;
            this.left = left;
            this.right = right;
            this.durationMS = durationMS;
        }
        
        @Override
        public boolean continueLoop() {
            return (System.currentTimeMillis() - start) < durationMS;
        }

        @Override
        public void runLoop() {
            if (start == 0) {
                start = System.currentTimeMillis();
            }

            drive.tankDrive(left, right);
        }
    }
    
    class McBukkit implements Command {

        private DualActionSolenoid bucketSolenoid;
        
        /**
         * Runs the specified solenoid. Reference!
         * @param bucketSolenoid the solenoid to run
         */
        public McBukkit(DualActionSolenoid bucketSolenoid) {
            this.bucketSolenoid = bucketSolenoid;
        }
        
        @Override
        public void run() {
            bucketSolenoid.set(Direction.RIGHT);
        }
      }
}