The AppleCore Programming Language, v1.0
========================================
Copyright (C) 2011-13 by Rob Bocchino

1\. Introduction and Rationale
------------------------------

The goal of AppleCore is to provide a "low-level high-level" language
for writing programs that run on the Apple II series of computers.
For more information on the design and goals of AppleCore, see
Docs/AppleCore-Spec-v1.0.pdf.

2\. License
-----------

This software and the accompanying documentation are provided free for
general use, _provided that attribution is maintained_.  Any public
copying or distribution of this work or any derivative works thereof
must clearly state the original author's contribution.

Of course it is hoped this software works as intended.  However, it's
still under development and bugs can always occur.  Therefore, this
software is provided "as is" without any warranty, express or implied,
that it conforms to its specification or otherwise does anything in
particular, other than what it actually does do when you run it.

The file Editors/Emacs/caps-lock.el is subject to the GNU General Public
License, v3.  See the directory Editors/Emacs for further details.

3\. System Requirements
-----------------------

If you just want to run the sample programs located in the DOS3.3
directory, then you can use any Apple II emulator that reads DOS 3.3
disk images.

To compile AppleCore programs from source, you will need the
following:

1.  An Apple Macintosh computer running OS X 10.4 or later, with XCode
    command line tools installed.

2.  The Virtual ][ emulator, available at http://www.virtualii.com/.

3.  A Java 6 Virtual Machine and javac compiler.

4.  A Standard ML of New Jersey (SML/NJ) installation, available via
    macports (http://www.macports.org) or via the developers
    (http://http://smlnj.cs.uchicago.edu/dist/working/110.76/index.html).

With minor modifications it should be possible to use a different
emulator and/or a different host OS (Linux should be straightforward;
Windows will require the usual modifications of build scripts to
replace Unix-style paths with Windows paths).

4\. Mac Setup
-------------

To set up your Mac to use AppleCore, do the following:

1.  If necessary install Java, XCode, Virtual ][, and/or SML/NJ.

2.  Download the AppleCore distribution from the git repository:

    `git clone git://github.com/bocchino/AppleCore.git`

3.  Set the environment variable APPLECORE to point to the top-level
    directory of the AppleCore distribution.

4.  Set the environment variable SMLNJ_HOME to point to the directory
    containing the bin directory of your SML/NJ installation. (If you
    installed SML/NJ via MacPorts, this should be /opt/local/share/smlnj.
    If you installed SML/NJ from the developers' web site, this is the
    top-level installation directory.) 

5.  Include $APPLECORE/Compiler/bin, $APPLECORE/Assembler/bin, and
    $APPLECORE/Scripts in your UNIX PATH.

6.  _Optional:_ If you use Emacs and/or Vim, add the AppleCore and AVM support
    to your Emacs and/or Vim installations.  See
    $APPLECORE/Editors/Emacs/README.md and $APPLECORE/Editors/Vim/README.md 
    for details.

7.  Build the project:

    `cd $APPLECORE`

    `make`

The build should succeed without any errors.  If not, fix the problem
and try again until it works.

5\. Writing AppleCore Programs
------------------------------

Currently the best documentation for the AppleCore language is the
spec ($APPLECORE/Docs/AppleCore-Spec-v1.0.pdf).  However, like most
language specs it's a bit dry and conveys all the details without
enough worked examples.  Unfortunately there's no tutorial
documentation yet.  However, after browsing the spec to get the
general idea of what's going on, you should be able to read the
examples in $APPLECORE/Programs to get a better idea of how to write
programs in AppleCore.

6\. Compiling AppleCore Programs
--------------------------------

To compile an AppleCore program, you must carry out the following
steps:

1.  Run the AppleCore compiler (acc) to translate one or more AppleCore
    source files FILE.ac into AppleCore Virtual Machine (AVM) assembly
    files FILE.avm.

2.  Run the AppleCore assembler (sc-avm-asm) to translate the AVM
    assembly files into binary files that can be run on the Apple II.

To see how this is done, go to the directory
`$APPLECORE/Programs/Examples/HelloWorld`.  First, peek at the
source program ac/HELLO.WORLD.ac.  That's the program we'll compile.
Next, type `make clean` and then `make`.  A directory `obj` should
appear containing the binary executable file `HELLO.WORLD.OBJ`.

If you would like to see the assembled output listing, then say `make
OPTS=-l`.  That tells the assembler to list the assembly to the
standard output (which can be redirected to a file).  

Notice that the assembled program is quite a bit longer than the
source program!  That's because some library and runtime code has been
assembled into the final program.

Notice also that the compiler and the assembler both require options
indicating where to find included files.  Those options are specified
in the file $APPLECORE/Programs/Examples/HelloWorld/Makefile.  See
sections 8 and 9 below for more information about these options.

7\. Running AppleCore Programs
------------------------------

One nice thing about Virtual ][ is that it reads and writes OS X
directories as if they were virtual DOS 3.3 disks.  This feature makes
it easy to transfer files between the Mac and the emulator.

To run a compiled AppleCore program, start up Virtual ][ and boot DOS
3.3.  Drag the directory containing the file you want to run into one
of the emulator's virtual disk drives.  Now DOS commands run from
inside the emulator can "see" the files in the mounted directory as
Apple II DOS files.

For example, to run the "hello world" program, drag
`$APPLECORE/Programs/Examples/HelloWorld/obj` into one of the
drives, say drive 1.  Virtual ][ will display a dialog box asking you
about the file format.  You want "No Conversion" (the default), so
just click OK.  The disk should mount in the drive.  Next say `BRUN
HELLO.WORLD.OBJ` at the BASIC prompt.  The Apple II should respond by
printing

   `HELLO, WORLD!`

to the screen.

Using Virtual ][, file management on the Mac is easy: Virtual ][ reads
in the binary files exactly as they are stored on the Mac, so (for
example) you can make a virtual disk with multiple files in it simply
by copying all the files to an OS X directory, and mounting it.  If
you wish, you can (1) use a utility like Copy II Plus
(http://www.vectronicsappleworld.com/appleii/internet.html#copy) to
make DOS 3.3 disk image files (this is particularly handy for creating
bootable disk images) and/or (2) send the contents of an OS X
directory or disk image to an actual floppy, using an actual Apple II.
See the Virtual ][ documentation for more details.


8\. The AppleCore Compiler (acc)
-------------------------------

The acc options are described below.  If you use one of the predefined
compilation patterns in $APPLECORE/Common, then you can avoid
invoking acc or specifying these options directly.  See
$APPLECORE/Common/README.md.

acc accepts exactly one source file name SF (including UNIX path info)
on the command line, translates that file, and writes the results to
standard output.  In the future, more flexible options (e.g.,
compiling multiple files in one acc command) may be provided.

acc accepts the following command-line options:

  - `-decls=DF1:...:DFn`

    Before translating SF, parse files DF1 through DFn and get the
    declarations out of them.  This allows SF to refer to functions,
    constants, data, or variables declared in any of the DFi, which is
    essential for separate compilation.

  - `-include`

    Translate SF in include mode (see Section 5.1 of the AppleCore
    specification).  If no -include appears on the command line, then
    the default is top-level mode.

  - `-native`

    Generate 6502 code that calls into the AVM runtime, instead of
    interpreted AVM code.  This should increase execution speed about
    2x, and increase code size about 3x.

  - `-origin=OR`

    Instruct the assembler to assemble the file with origin
    address OR.  The origin address may be given in positive decimal,
    negative decimal, or hexadecimal preceded by $.  If no -origin=
    appears on the command line, then for top level mode the default
    origin is $803.  That puts the start of the program in the main
    storage area for programs and data, just above the three bytes
    signaling an empty BASIC program.  For include mode the default is
    to use the origin implied by the point in the program where the
    file is included.

  - `-tf=TF`

    Instruct the assembler to write the output to file TF.  (TF stands
    for "text file," which is how the assembler refers to its file
    output.)  If no -tf= appears on the command line, then the default
    name is generated by stripping .AC from the end of SF (if it is
    there, ignoring case) and adding .OBJ.

These compiler options handle most common cases. Finer control over
what goes where in memory can be achieved by compiling everything in
include mode and writing a short assembly language program to glue the
pieces together.  You might do this if the whole program won't fit in
memory, or if you need the program to occupy discontinuous parts of
memory (e.g., to wrap it around the hi-res graphics pages).  See
$APPLECORE/Programs/Examples/Chain for an example of how to do this.

9\. The SC-AVM-ASM Assembler
----------------------------

The assembler is called sc-avm-asm because its format is based on the
SC Macro Assembler.  This was my favorite assembler back when I first
wrote assembly code on the Apple II.  Also, an earlier version of the
AppleCore tool chain actually used this assembler (and required you to
do final assembly on the Apple II).

The SC Macro Assembler is an impressive piece of engineering, but it
suffers from the inherent limitations of the Apple II.  The current
assembler makes it much easier to use AppleCore, because (1) you can
do everything using UNIX tools and scripts, (2) you aren't constrained
by the Apple II's memory limitations, and (3) nested .IN directives
are allowed.

**Input format:** The assembler assembles three kinds of mnemonics,
using the SC Macro Assembler format:

1.  Native 6502 instructions

2.  AppleCore Virtual Machine (AVM) instructions.  The AppleCore
    compiler compiles AppleCore source files to AVM assembly language.
    The assembler translates this assembly language to byte code that
    is interpreted by the AVM runtime.  For more details on how this
    works, see the AVM specification
    ($APPLECORE/Docs/AVM-Spec-v1.0.pdf).

3.  A subset of the SC Macro Assembler directives.  Most directives
    are supported, except for the ones that don't make sense when
    cross-assembling (e.g., the .TA directive, which specifies where
    in memory to put the assembled code).  Macros and private labels
    are not supported.

You can get documentation on the SC Macro Assembler format and
directives here: http://stjarnhimlen.se/apple2/.  

**Options:** The sc-avm-scm options are described below.  If you use
one of the predefined compilation patterns in $APPLECORE/Common,
then you can avoid invoking sc-avm-scm or specifying these options
directly.  See $APPLECORE/Common/README.md.

sc-avm-scm accepts the following command-line options:

  - `-d outdir`

    Set the output directory to outdir.  All output files, including
    any files specified by .TF directives, are written to that
    directory.  If no -d option is specified, then the default is the
    current working directory.

  - `-i p1:...:pn`

    Search paths p1 through pn (in that order) for AVM files to
    include when encountering an .IN directive.

  - `-l`

    List the assembly file to stdout.

  - `-o outfile`

    Set the output file to outfile.  The file is interpreted relative
    to the outdir; that is, the output is written to outdir/outfile.
    The default is generated by stripping .AVM from the end of the
    input file name (if it is there, ignoring case) and adding .OBJ.

**Generating output files:** Please note the following:

  - The .TF (text file) assembler directive works just like in the SC
    Macro Assembler, so the initial output file (default or set by -o)
    is overridden by any .TF directive(s) encountered.

  - Because DOS file names must be upper case, the assembler converts
    all file names (default or specified by -o or .TF) to upper case.

The neat thing about the .TF directive is that it lets you assemble a
single logical program into multiple output files.  This is handy, for
example, when your whole program won't fit into memory.  See
$APPLECORE/Programs/Examples/Chain and
$APPLECORE/Programs/Games/BelowTheBasement for examples of the .TF
directive in action.

9\. Integration with BASIC and DOS
----------------------------------

For the most part, loading and running AppleCore programs should work
seamlessly with BASIC and DOS (except that loading an AppleCore
program clobbers any resident BASIC program, of course).  AppleCore
programs patch DOS while they are running to make error handling work
less insanely, but they always restore DOS to its usual ways on exit,
even via control-reset.  AppleCore programs can be run from the
monitor prompt, the Integer BASIC prompt, or the AppleSoft prompt.

The one exception is that to maintain compatibility with whatever the
environment is doing with control-reset, AppleCore programs always
exit via a JMP to the location stored in the reset vector ($3F2-$3F3)
on program startup.  When DOS 3.3 is booted, this is typically $9DBF
(DOS warm start).  Thus, in most cases, if you want to load a BASIC
program after running an AppleCore program, you should first say INT
or FP (or 3D3G or CONTROL-B from inside the monitor) to reset the
BASIC environment.  Otherwise you may get an OUT OF MEMORY or MEM FULL
error when you attempt to load the BASIC program.

Currently AppleCore works with DOS 3.3 and DOS 3.3 only; ProDOS is not
supported.  I've no plans to change that any time soon.  I'm most
interested in the "old school" Apple II with Woz's original monitor,
integer BASIC, and the 40 column display installed.  The only newer
firmware features I really care about are booting DOS (essential) and
lower-case characters (nice, but not essential).  Other features like
80-character displays, double hi-res graphics, alternate character
sets, auxiliary memories, etc. are nice, but they are also a headache
to program and destroy the simplicity and elegance of Woz's original
design.

10\. Shell Editor
-----------------

As part of the AppleCore release, I've included a nifty little shell
editor that's fun to use from the monitor or BASIC prompt and is
automatically invoked by any program that does a JSR to $FD6F (GETLN1)
to get line input.  It works quite nicely with the old monitor and the
solid flashing cursor!  For more details, see the documentation in
$APPLECORE/DOS3.3/README.md and the source code in
$APPLECORE/ShellEditor.

