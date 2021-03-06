package ca.fourthreethreefour;

import java.io.File;
import java.io.IOException;

import ca.fourthreethreefour.commands.ReverseDualActionSolenoid;
import ca.fourthreethreefour.commands.debug.Logging;
import ca.fourthreethreefour.settings.AutoFile;
import edu.first.command.Command;
import edu.first.command.Commands;
import edu.first.commands.ThreadedCommand;
import edu.first.commands.common.LoopingCommand;
import edu.first.identifiers.InversedSpeedController;
import edu.first.module.Module;
import edu.first.module.actuators.DualActionSolenoid;
import edu.first.module.actuators.DualActionSolenoid.Direction;
import edu.first.module.joysticks.BindingJoystick.AxisBind;
import edu.first.module.joysticks.BindingJoystick.DualAxisBind;
import edu.first.module.joysticks.XboxController;
import edu.first.module.subsystems.Subsystem;
import edu.first.robot.IterativeRobotAdapter;
import edu.wpi.first.wpilibj.CameraServer;
import edu.wpi.first.wpilibj.PowerDistributionPanel;
import edu.wpi.first.wpilibj.Timer;

public class Robot extends IterativeRobotAdapter {
    private final Subsystem AUTO_MODULES = new Subsystem(
            new Module[] { drive, bucket, groundIntake, tunedDrive });

    private final Subsystem TELEOP_MODULES = new Subsystem(
            new Module[] { drive, climber, bucket, groundIntake, indicator, controllers, tunedDrive });

    private final Subsystem ALL_MODULES = new Subsystem(new Module[] { AUTO_MODULES, TELEOP_MODULES });

    public Robot() {
        super("ATA 2017");
    }

    PowerDistributionPanel panel = new PowerDistributionPanel();
    
    @Override
    public void init() {
        if (ROBOT_TYPE == "") {
            throw new Error("No ROBOT_TYPE set, please set a robot type in the settings file");
        }
        
        if(ROBOT_TYPE == "Practice") {
            throw new Error("Wrong code deploy, dummy");
        }

        ALL_MODULES.init();
        drivetrain.setExpiration(0.1);
        turningPID.setTolerance(TURN_TOLERANCE);
        distancePID.setTolerance(DISTANCE_TOLERANCE);
        
        CameraServer.getInstance().startAutomaticCapture();

        controller1.addDeadband(XboxController.LEFT_FROM_MIDDLE, 0.20);
        controller1.changeAxis(XboxController.LEFT_FROM_MIDDLE, speedFunction);

        controller1.addDeadband(XboxController.RIGHT_X, 0.20);
        controller1.invertAxis(XboxController.RIGHT_X);
        controller1.changeAxis(XboxController.RIGHT_X, turnFunction);
        
        controller1.addDeadband(XboxController.LEFT_TRIGGER, 0.10);
        
        if (MANUAL_CONTROL) {
            controller1.addAxisBind(new DualAxisBind(controller1.getLeftDistanceFromMiddle(), controller1.getRightX()) {
                @Override
                public void doBind(double speed, double turn) {
                    if (turn == 0 && speed == 0) {
                        drivetrain.stopMotor();
                    } else {
                        turn += (speed > 0) ? DRIVE_COMPENSATION : 0;
                        drivetrain.arcadeDrive(speed, turn);
                    }
                }
            });
        } else {
            controller1.addAxisBind(new DualAxisBind(controller1.getLeftDistanceFromMiddle(), controller1.getRightX()) {
                boolean isTurning = true;
                
                @Override
                public void doBind(double speed, double turn) {
                    if (turn == 0) {
                        if (isTurning) {
                            isTurning = false;
                            turningPID.setSetpoint(navx.getAngle());
                        }

                        drivetrain.arcadeDrive(speed, turnOutput.get());
                    } else {
                        isTurning = true;
                        drivetrain.arcadeDrive(speed, turn);
                    }
                }
            });
        }
        
        //reverse this
        AxisBind groundIntakeBind = new AxisBind(controller1.getRightTrigger(), new InversedSpeedController(groundIntake));
        controller1.addAxisBind(groundIntakeBind);
        
        controller1.addWhilePressed(XboxController.RIGHT_TRIGGER, new Command() {

            @Override
            public void run() {
                double intakeCurrentDraw = panel.getCurrent(INTAKE_PDP_PORT);
                if (intakeCurrentDraw > INTAKE_AMP_THRESHOLD) {
                    controller1.rumble(0.5);
                }
                
                Logging.put("Intake Current Draw", intakeCurrentDraw);
            }
        });
        
        controller1.addWhenPressed(XboxController.RIGHT_BUMPER, new ReverseDualActionSolenoid(bucketSolenoid));

        // run ground intake when retracting gear
        final long INTAKE_TIME = 2000L;
        controller1.addWhenPressed(XboxController.RIGHT_BUMPER, new ThreadedCommand(new LoopingCommand() {
            long start = 0;
            
            @Override
            public void firstLoop() {
                controller1.removeBind(groundIntakeBind);
            }
            
            @Override
            public void runLoop() {
                //reverse this
                new InversedSpeedController(groundIntake).set(1);
            }
            
            @Override
            public boolean continueLoop() {
                if (start == 0) {
                    if (bucketSolenoid.get() == BUCKET_IN) {
                        return false;
                    }
                    
                    start = System.currentTimeMillis();
                }
                return System.currentTimeMillis() - start < INTAKE_TIME;
            }
            
            @Override
            public void end() {
                groundIntake.set(0);
                controller1.addAxisBind(groundIntakeBind);
                start = 0;
            }
        }));
        
        controller1.addAxisBind(XboxController.LEFT_TRIGGER, climberMotors);
        
        //CameraServer.getInstance().startAutomaticCapture();
    }
    
    private Command autoCommand;
    
    @Override
    public void initDisabled() {
//        allianceSwitch.enable();
    }
    
    @Override
    public void periodicDisabled() {
        if (AUTO_TYPE == "") { return; }
        String alliance = ""; /* AUTO_ALLIANCE_INDEPENDENT ? "" : (allianceSwitch.getPosition() ? "red-" : "blue-"); */
        try {
            autoCommand = new AutoFile(new File(alliance + AUTO_TYPE + ".txt")).toCommand();
        } catch (IOException e) {
            // try alliance independent as backup
            try {
                autoCommand = new AutoFile(new File(AUTO_TYPE + ".txt")).toCommand();
            } catch (IOException i) {
                throw new Error(e.getMessage());
            }
        }
        
        Timer.delay(1);
    }

    @Override
    public void initAutonomous() {
        AUTO_MODULES.enable();
        drivetrain.setSafetyEnabled(false);
        Commands.run(autoCommand);
        drivetrain.stopMotor();
    }

    @Override
    public void endAutonomous() {
        drivetrain.setSafetyEnabled(true);
        AUTO_MODULES.disable();
    }

    @Override
    public void initTeleoperated() {
        TELEOP_MODULES.enable();
        if (bucketSolenoid.get() == Direction.OFF) {
            bucketSolenoid.set(DualActionSolenoid.Direction.LEFT);
        }
        turningPID.enable();
    }

    @Override
    public void periodicTeleoperated() {
        controller1.doBinds();
//        System.out.println(allianceSwitch.getPosition());

        Logging.log("left: " + leftEncoder.get() + " right: " + rightEncoder.get() + "\n");
        Logging.log("turning: " + navx.getAngle() + "\n");
        
        if (bucketSolenoid.get() == Direction.LEFT) {
            indicator.set(edu.first.module.actuators.SpikeRelay.Direction.FORWARDS);
        } else {
            indicator.set(edu.first.module.actuators.SpikeRelay.Direction.OFF);
        }
        
        //SmartDashboard.putNumber("Turning PID", turningPID.get());
        //SmartDashboard.putNumber("Turning Error", turningPID.getError());
        //SmartDashboard.putNumber("Turning Setpoint", turningPID.getSetpoint());
        //SmartDashboard.putNumber("Encoder Rate", driveEncoder.getRate());
        //SmartDashboard.putNumber("Encoder Position", driveEncoder.getPosition());
        //SmartDashboard.putBoolean("Has Gear", bucketSwitch.getPosition());
    }

    @Override
    public void endTeleoperated() {
        TELEOP_MODULES.disable();
    }
}
